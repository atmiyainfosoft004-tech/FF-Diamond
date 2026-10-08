package com.example.ffdiamond.ui.drag

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.core.view.isGone
import com.example.ffdiamond.R
import com.example.ffdiamond.apps.AppUninstaller
import com.example.ffdiamond.home.GridReorder
import com.example.ffdiamond.home.HomeLayoutRepository
import com.example.ffdiamond.model.Container
import com.example.ffdiamond.model.HomeItem
import com.example.ffdiamond.ui.DeviceProfile
import com.example.ffdiamond.ui.folder.FolderTitles
import com.example.ffdiamond.ui.workspace.CellLayout
import com.example.ffdiamond.ui.workspace.FolderIconView
import com.example.ffdiamond.ui.workspace.IconCellView
import com.example.ffdiamond.util.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Everything that happens between lifting an icon and letting it go.
 *
 * The controller owns no layout of its own. It reads positions out of the workspace, decides what a
 * release at the current point would mean, shows that decision as a preview, and on release turns
 * it into database writes — the layout then comes back through the normal flow and the views follow.
 * Nothing is moved directly, which is why killing the process mid-drag loses the drag and not the
 * home screen.
 */
class DragController(
    private val dragLayer: DragLayer,
    private val actionBar: DropActionBar,
    private val host: WorkspaceHost,
    private val repository: HomeLayoutRepository,
    private val scope: CoroutineScope,
    private val profile: () -> DeviceProfile,
    private val dock: CellLayout,
    private val onDragStateChanged: (Boolean) -> Unit
) {

    private val context: Context = dragLayer.context
    private val temp = Rect()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var active: ActiveDrag? = null

    val isDragging: Boolean get() = active != null

    private class ActiveDrag(
        val item: HomeItem,
        val sourceView: IconCellView,
        val sourceContainer: Container,
        val sourcePage: Int,
        val view: DragView,
        val grabDx: Float,
        val grabDy: Float,
        val iconSize: Int,
        val sourceIconRect: Rect
    ) {
        var pointerX = 0f
        var pointerY = 0f

        var previewPage: CellLayout? = null
        var plan: GridReorder.Plan? = null
        var planContainer: Container = Container.DESKTOP
        var planPage: Int = 0

        var mergeView: IconCellView? = null
        var mergeSince = 0L
        var mergeArmed = false
        var mergeAnimation: ValueAnimator? = null

        var edgeDirection = 0
        var edgeSince = 0L
        var lifted = false
        var downX = 0f
        var downY = 0f
    }

    // ------------------------------------------------------------------- start

    /**
     * Lifts [sourceView] off the grid. The touch point comes from the drag layer rather than from
     * the caller, because a long press does not report one.
     */
    fun startDrag(
        item: HomeItem,
        sourceView: IconCellView,
        container: Container,
        dbPage: Int
    ): Boolean {
        if (isDragging) return false

        val iconRect = Rect().also { sourceView.iconRect(it) }
        if (iconRect.isEmpty) return false

        val bitmap = rasterise(sourceView, iconRect) ?: return false
        val layerRect = Rect(iconRect)
        dragLayer.offsetDescendantRectToMyCoords(sourceView, layerRect)

        val dragView = DragView(context, bitmap, iconRect.width())
        dragLayer.addView(
            dragView,
            ViewGroup.LayoutParams(iconRect.width(), iconRect.height())
        )
        dragView.x = layerRect.left.toFloat()
        dragView.y = layerRect.top.toFloat()

        val drag = ActiveDrag(
            item = item,
            sourceView = sourceView,
            sourceContainer = container,
            sourcePage = dbPage,
            view = dragView,
            grabDx = (dragLayer.lastTouchX - layerRect.left)
                .coerceIn(0f, iconRect.width().toFloat()),
            grabDy = (dragLayer.lastTouchY - layerRect.top)
                .coerceIn(0f, iconRect.height().toFloat()),
            iconSize = iconRect.width(),
            sourceIconRect = layerRect
        )
        drag.pointerX = dragLayer.lastTouchX
        drag.pointerY = dragLayer.lastTouchY
        drag.downX = dragLayer.lastTouchX
        drag.downY = dragLayer.lastTouchY
        dragView.visibility = View.INVISIBLE
        active = drag

        // Some ancestor may have asked for the gesture to stay put when the press began; the drag
        // outranks that, so the flag is cleared before the first move arrives.
        dragLayer.requestDisallowInterceptTouchEvent(false)
        dragLayer.postOnAnimation(ticker)
        onDragStateChanged(true)
        return true
    }

    private fun beginLift(drag: ActiveDrag) {
        if (drag.lifted) return
        drag.lifted = true
        host.setItemHidden(drag.item.id, hidden = true)
        drag.view.visibility = View.VISIBLE
        lift(drag.view)
        actionBar.show(canUninstall = uninstallableApp(drag.item) != null)
    }

    private fun lift(view: DragView) {
        val duration = Motion.duration(context, Motion.DURATION_SMALL)
        view.elevation = 0f
        view.animate()
            .scaleX(LIFT_SCALE)
            .scaleY(LIFT_SCALE)
            .setDuration(duration)
            .setInterpolator(Motion.SMALL)
            .start()
        ValueAnimator.ofFloat(0f, context.resources.getDimension(R.dimen.drag_elevation)).apply {
            this.duration = duration
            interpolator = Motion.SMALL
            addUpdateListener { view.elevation = it.animatedValue as Float }
            start()
        }
    }

    private fun rasterise(view: IconCellView, iconRect: Rect): Bitmap? {
        if (iconRect.width() <= 0 || iconRect.height() <= 0) return null
        val bitmap = createBitmap(iconRect.width(), iconRect.height())
        val canvas = Canvas(bitmap)
        canvas.translate(-iconRect.left.toFloat(), -iconRect.top.toFloat())
        view.drawIconInto(canvas)
        return bitmap
    }

    // -------------------------------------------------------------------- move

    fun onDragTouch(event: MotionEvent): Boolean {
        val drag = active ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                drag.pointerX = event.x
                drag.pointerY = event.y
                if (!drag.lifted) {
                    if (hypot(event.x - drag.downX, event.y - drag.downY) <= touchSlop) return true
                    beginLift(drag)
                }
                drag.view.follow(event.x - drag.grabDx, event.y - drag.grabDy)
                updateHover(drag)
            }

            MotionEvent.ACTION_UP -> {
                drag.pointerX = event.x
                drag.pointerY = event.y
                if (!drag.lifted) {
                    abortWithoutMove(drag)
                } else {
                    drop(drag)
                }
            }

            MotionEvent.ACTION_CANCEL -> cancelDrag()
        }
        return true
    }

    /**
     * Re-evaluates the hover every frame rather than only on movement.
     *
     * Both hold gestures — 250 ms over an icon to merge, 400 ms at an edge to turn the page — are
     * about the finger *not* moving, so a listener that only wakes on `ACTION_MOVE` would never
     * fire either of them.
     */
    private val ticker = object : Runnable {
        override fun run() {
            val drag = active ?: return
            if (drag.lifted) updateHover(drag)
            dragLayer.postOnAnimation(this)
        }
    }

    private fun updateHover(drag: ActiveDrag) {
        val barTarget = actionBar.targetAt(drag.pointerX, drag.pointerY, iconRectOf(drag))
        actionBar.setHovered(barTarget)
        if (barTarget != null) {
            clearMerge(drag)
            clearPreview(drag)
            return
        }

        updateEdgeFlip(drag)

        val target = cellUnder(drag) ?: run {
            clearMerge(drag)
            clearPreview(drag)
            return
        }

        val occupant = target.page.childAt(target.cellX, target.cellY) as? IconCellView
        val occupantItem = occupant?.homeItem()
        // Folders do not merge into other cells: dropping a folder on an app would nest one
        // folder inside another, which this launcher does not represent.
        val mergeable = occupant != null &&
            occupantItem != null &&
            occupantItem.id != drag.item.id &&
            drag.item is HomeItem.App &&
            withinMergeRadius(target)

        if (mergeable) {
            clearPreview(drag)
            armMerge(drag, occupant)
        } else {
            clearMerge(drag)
            showReorder(drag, target)
        }
    }

    private class CellTarget(
        val page: CellLayout,
        val container: Container,
        val dbPage: Int,
        val cellX: Int,
        val cellY: Int,
        val localX: Float,
        val localY: Float
    )

    private fun cellUnder(drag: ActiveDrag): CellTarget? {
        val dockHit = localPoint(dock, drag.pointerX, drag.pointerY)
        if (dockHit != null) {
            val (cellX, cellY) = dock.cellAt(dockHit.first, dockHit.second)
            return CellTarget(dock, Container.DOCK, 0, cellX, cellY, dockHit.first, dockHit.second)
        }

        val dbPage = host.visiblePage()
        val page = host.pageAt(dbPage) ?: return null
        val hit = localPoint(page, drag.pointerX, drag.pointerY) ?: return null
        val (cellX, cellY) = page.cellAt(hit.first, hit.second)
        return CellTarget(page, Container.DESKTOP, dbPage, cellX, cellY, hit.first, hit.second)
    }

    /** Null when the point is outside the view; otherwise the point in the view's own space. */
    private fun localPoint(view: View, layerX: Float, layerY: Float): Pair<Float, Float>? {
        val left = viewLeftInLayer(view)
        val top = viewTopInLayer(view)
        val x = layerX - left
        val y = layerY - top
        if (x < 0 || y < 0 || x > view.width || y > view.height) return null
        return x to y
    }

    private fun viewLeftInLayer(view: View): Int {
        temp.set(0, 0, 0, 0)
        dragLayer.offsetDescendantRectToMyCoords(view, temp)
        return temp.left
    }

    private fun viewTopInLayer(view: View): Int {
        temp.set(0, 0, 0, 0)
        dragLayer.offsetDescendantRectToMyCoords(view, temp)
        return temp.top
    }

    private fun withinMergeRadius(target: CellTarget): Boolean {
        target.page.cellRect(target.cellX, target.cellY, temp)
        val distance = hypot(target.localX - temp.exactCenterX(), target.localY - temp.exactCenterY())
        return distance <= profile().iconSizePx * MERGE_RADIUS_RATIO
    }

    // ------------------------------------------------------------------ merge

    private fun armMerge(drag: ActiveDrag, candidate: IconCellView) {
        if (drag.mergeView !== candidate) {
            clearMerge(drag)
            drag.mergeView = candidate
            drag.mergeSince = SystemClock.uptimeMillis()
            return
        }
        if (drag.mergeArmed) return

        val hold = Motion.duration(context, Motion.DURATION_MERGE_HOLD)
        if (SystemClock.uptimeMillis() - drag.mergeSince < hold) return

        drag.mergeArmed = true
        val grow = Motion.duration(context, Motion.DURATION_MERGE_GROW)
        if (grow == 0L) {
            candidate.setMergeProgress(1f)
            return
        }
        drag.mergeAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = grow
            interpolator = Motion.OVERSHOOT
            addUpdateListener { candidate.setMergeProgress(it.animatedValue as Float) }
            start()
        }
    }

    private fun clearMerge(drag: ActiveDrag) {
        drag.mergeAnimation?.cancel()
        drag.mergeAnimation = null
        drag.mergeView?.setMergeProgress(0f)
        drag.mergeView = null
        drag.mergeArmed = false
        drag.mergeSince = 0L
    }

    // --------------------------------------------------------------- reordering

    private fun showReorder(drag: ActiveDrag, target: CellTarget) {
        val (occupants, blocked) = occupantsOf(target.page, exclude = drag.item.id)
        val targetIndex = GridReorder.indexOf(target.cellX, target.cellY, target.page.columns)
        val plan = GridReorder.plan(occupants, target.page.capacity, targetIndex, blocked)

        if (plan == null) {
            clearPreview(drag)
            return
        }
        if (drag.previewPage !== target.page) clearPreview(drag)

        drag.previewPage = target.page
        drag.plan = plan
        drag.planContainer = target.container
        drag.planPage = target.dbPage

        val moves = HashMap<View, Int>(plan.moves.size)
        plan.moves.forEach { (id, index) ->
            viewFor(target.page, id)?.let { moves[it] = index }
        }
        target.page.applyReorderPreview(moves)
    }

    private fun clearPreview(drag: ActiveDrag) {
        drag.previewPage?.clearReorderPreview()
        drag.previewPage = null
        drag.plan = null
    }

    private fun occupantsOf(page: CellLayout, exclude: Long): Pair<Map<Int, Long>, Set<Int>> {
        val occupants = HashMap<Int, Long>(page.childCount)
        val blocked = HashSet<Int>()
        for (i in 0 until page.childCount) {
            val child = page.getChildAt(i)
            if (child.isGone) continue
            val params = child.layoutParams as? CellLayout.LayoutParams ?: continue
            val item = child.homeItem()
            if (item != null) {
                if (item.id == exclude) continue
                if (item is HomeItem.Widget || params.spanX * params.spanY > 1) {
                    for (y in params.cellY until params.cellY + params.spanY) {
                        for (x in params.cellX until params.cellX + params.spanX) {
                            blocked += GridReorder.indexOf(x, y, page.columns)
                        }
                    }
                } else {
                    occupants[page.indexOf(child)] = item.id
                }
            } else {
                for (y in params.cellY until params.cellY + params.spanY) {
                    for (x in params.cellX until params.cellX + params.spanX) {
                        blocked += GridReorder.indexOf(x, y, page.columns)
                    }
                }
            }
        }
        return occupants to blocked
    }

    private fun viewFor(page: CellLayout, id: Long): View? {
        for (i in 0 until page.childCount) {
            val child = page.getChildAt(i)
            if (child.homeItem()?.id == id) return child
        }
        return null
    }

    // ------------------------------------------------------------- page turning

    private fun updateEdgeFlip(drag: ActiveDrag) {
        val zone = dragLayer.width * EDGE_ZONE_RATIO
        val direction = when {
            drag.pointerX < zone -> -1
            drag.pointerX > dragLayer.width - zone -> 1
            else -> 0
        }

        if (direction != drag.edgeDirection) {
            drag.edgeDirection = direction
            drag.edgeSince = SystemClock.uptimeMillis()
            return
        }
        if (direction == 0) return

        val hold = Motion.duration(context, Motion.DURATION_PAGE_FLIP_HOLD)
        if (SystemClock.uptimeMillis() - drag.edgeSince < hold) return
        drag.edgeSince = SystemClock.uptimeMillis()

        val current = host.visiblePage()
        val next = current + direction
        when {
            next < 0 -> Unit
            next < host.pageCount() -> {
                clearPreview(drag)
                clearMerge(drag)
                host.goToPage(next)
            }
            // Dragging past the last page makes a new one, which is thrown away again on release
            // if the icon did not end up there.
            else -> {
                clearPreview(drag)
                clearMerge(drag)
                host.goToPage(host.ensureTrailingEmptyPage())
            }
        }
    }

    // -------------------------------------------------------------------- drop

    private fun drop(drag: ActiveDrag) {
        // Classify the drop against the bar *before* hide() starts sliding it away.
        val barTarget = actionBar.targetAt(drag.pointerX, drag.pointerY, iconRectOf(drag))
        stop()

        when {
            barTarget == DropActionBar.Target.REMOVE -> finishByRemoving(drag)
            barTarget == DropActionBar.Target.UNINSTALL -> finishByUninstalling(drag)
            drag.mergeArmed && drag.mergeView != null -> finishByMerging(drag, drag.mergeView!!)
            drag.plan != null -> finishByPlacing(drag, drag.plan!!)
            else -> finishByReturning(drag)
        }
    }

    fun cancelDrag() {
        val drag = active ?: return
        stop()
        if (drag.lifted) finishByReturning(drag) else abortVisuals(drag)
    }

    private fun abortWithoutMove(drag: ActiveDrag) {
        stop()
        abortVisuals(drag)
    }

    private fun abortVisuals(drag: ActiveDrag) {
        drag.view.remove()
        host.setItemHidden(drag.item.id, hidden = false)
        host.discardTrailingEmptyPage()
    }

    /** Ends the gesture side of the drag; the drop animation outlives it. */
    private fun stop() {
        val drag = active ?: return
        dragLayer.removeCallbacks(ticker)
        actionBar.hide()
        drag.view.releaseSprings()
        drag.mergeAnimation?.cancel()
        drag.mergeView?.setMergeProgress(0f)
        active = null
        onDragStateChanged(false)
    }

    private fun finishByPlacing(drag: ActiveDrag, plan: GridReorder.Plan) {
        val page = drag.previewPage ?: return finishByReturning(drag)
        val columns = page.columns
        val (targetX, targetY) = GridReorder.cellOf(plan.targetIndex, columns)

        val draggedFolderId = (drag.item as? HomeItem.Folder)?.folderId
        val placements = plan.moves.map { (id, index) ->
            val (x, y) = GridReorder.cellOf(index, columns)
            HomeLayoutRepository.Placement(id, drag.planContainer, drag.planPage, x, y)
        } + HomeLayoutRepository.Placement(
            drag.item.id,
            drag.planContainer,
            drag.planPage,
            targetX,
            targetY,
            folderId = draggedFolderId
        )

        commit { repository.applyPlacements(placements) }

        page.cellRect(targetX, targetY, temp)
        val destination = iconRectIn(page, temp)
        animateInto(drag, destination, LANDED) { reveal(drag) }
    }

    private fun finishByMerging(drag: ActiveDrag, targetView: IconCellView) {
        val targetItem = targetView.homeItem() ?: return finishByReturning(drag)
        val dragged = drag.item

        // The icon shrinks into the exact mini-grid slot it is about to be redrawn in, so the merge
        // reads as being absorbed by the folder rather than as one icon covering another. The
        // database write waits until that animation ends: writing first would replace the target
        // app view with a folder view mid-flight, and the slot we are flying into would vanish.
        val plate = Rect().also { targetView.iconRect(it) }
        dragLayer.offsetDescendantRectToMyCoords(targetView, plate)
        val slot = Rect()
        FolderIconView.miniSlot(plate, memberIndexFor(targetItem), slot)

        animateInto(drag, slot, FolderIconView.MINI_ICON_RATIO) {
            commit {
                when (targetItem) {
                    is HomeItem.Folder ->
                        repository.addToFolder(dragged.id, targetItem.folderId, targetItem.members.size)

                    is HomeItem.App -> repository.createFolder(
                        title = FolderTitles.suggest(context, listOf(targetItem, dragged)),
                        container = targetItem.container,
                        page = targetItem.page,
                        cellX = targetItem.cellX,
                        cellY = targetItem.cellY,
                        memberIds = listOf(targetItem.id, dragged.id)
                    )

                    is HomeItem.Widget -> Unit
                }
            }
            drag.view.remove()
            host.setItemHidden(dragged.id, hidden = false)
            host.discardTrailingEmptyPage()
        }
    }

    /** Where the dropped icon will sit in the folder's preview: appended, or second in a new one. */
    private fun memberIndexFor(target: HomeItem): Int = when (target) {
        is HomeItem.Folder -> target.members.size.coerceAtMost(FolderIconView.GRID * FolderIconView.GRID - 1)
        is HomeItem.App -> 1
        is HomeItem.Widget -> 0
    }

    private fun finishByRemoving(drag: ActiveDrag) {
        commit { repository.remove(drag.item.id) }
        fadeOut(drag) { host.discardTrailingEmptyPage() }
    }

    private fun finishByUninstalling(drag: ActiveDrag) {
        val app = uninstallableApp(drag.item)
        if (app == null) {
            finishByReturning(drag)
            return
        }
        // Starting an activity in the same call as ACTION_UP is dropped on some devices; the
        // confirmation sheet has to come after the gesture has finished dispatching.
        val packageName = app.packageName
        val user = app.user
        dragLayer.post { AppUninstaller.start(context, packageName, user) }
        finishByReturning(drag)
    }

    /** The icon as currently drawn in the drag layer, which is what the user is aiming with. */
    private fun iconRectOf(drag: ActiveDrag): Rect {
        val view = drag.view
        val x = view.x.roundToInt()
        val y = view.y.roundToInt()
        return Rect(x, y, x + view.width, y + view.height)
    }

    private fun finishByReturning(drag: ActiveDrag) {
        drag.previewPage?.clearReorderPreview()
        animateInto(drag, drag.sourceIconRect, LANDED) { reveal(drag) }
        host.discardTrailingEmptyPage()
    }

    private fun reveal(drag: ActiveDrag) {
        drag.view.remove()
        host.setItemHidden(drag.item.id, hidden = false)
    }

    private fun commit(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    private fun animateInto(drag: ActiveDrag, destination: Rect, endScale: Float, onEnd: () -> Unit) {
        val view = drag.view
        val duration = Motion.duration(context, Motion.DURATION_STANDARD)
        if (duration == 0L) {
            onEnd()
            return
        }
        // Scaling about the centre means the target x/y have to account for the size the view will
        // appear to be, or a shrinking icon drifts down and right as it lands.
        val inset = drag.iconSize * (1f - endScale) / 2f
        view.animate()
            .x(destination.left - inset + (destination.width() - drag.iconSize) / 2f)
            .y(destination.top - inset + (destination.height() - drag.iconSize) / 2f)
            .scaleX(endScale)
            .scaleY(endScale)
            .setDuration(duration)
            .setInterpolator(Motion.STANDARD)
            .withEndAction(onEnd)
            .start()
        ValueAnimator.ofFloat(view.elevation, 0f).apply {
            this.duration = duration
            interpolator = Motion.STANDARD
            addUpdateListener { view.elevation = it.animatedValue as Float }
            start()
        }
    }

    private fun fadeOut(drag: ActiveDrag, onEnd: () -> Unit) {
        drag.view.animate()
            .alpha(0f)
            .scaleX(ABSORBED)
            .scaleY(ABSORBED)
            .setDuration(Motion.duration(context, Motion.DURATION_SMALL))
            .setInterpolator(Motion.SMALL)
            .withEndAction {
                drag.view.remove()
                onEnd()
            }
            .start()
    }

    private fun DragView.remove() = (parent as? ViewGroup)?.removeView(this)

    /**
     * Where an icon sits inside [cell].
     *
     * Every cell in a page is the same size, so an icon's offset within its cell is the same for
     * all of them — copying it off a view already in the page is exact, and avoids restating the
     * vertical centring rule that `IconCellView` owns. An empty page has nothing to copy from, in
     * which case the cell itself is close enough for the last frame of a landing animation.
     */
    private fun iconRectIn(page: CellLayout, cell: Rect): Rect {
        val sample = (0 until page.childCount)
            .asSequence()
            .map { page.getChildAt(it) }
            .filterIsInstance<IconCellView>()
            .firstOrNull()
            ?: return Rect(cell)

        val icon = Rect().also { sample.iconRect(it) }
        return Rect(
            cell.left + icon.left,
            cell.top + icon.top,
            cell.left + icon.right,
            cell.top + icon.bottom
        )
    }

    private fun uninstallableApp(item: HomeItem) =
        (item as? HomeItem.App)?.info?.takeUnless { it.isSystemApp }

    private companion object {
        const val LIFT_SCALE = 1.12f

        /** Back to life size when the icon settles into a cell. */
        const val LANDED = 1f

        /** Remove: shrinks away rather than flying to a cell that no longer exists. */
        const val ABSORBED = 0.4f

        const val MERGE_RADIUS_RATIO = 0.5f
        const val EDGE_ZONE_RATIO = 0.07f
    }
}

/** The layout row a cell view was bound from, or null for anything that is not a bound cell. */
fun View.homeItem(): HomeItem? = getTag(R.id.tag_home_item) as? HomeItem

fun View.setHomeItem(item: HomeItem) = setTag(R.id.tag_home_item, item)

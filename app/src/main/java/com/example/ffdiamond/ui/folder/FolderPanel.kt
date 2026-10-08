package com.example.ffdiamond.ui.folder

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.createBitmap
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import com.example.ffdiamond.R
import com.example.ffdiamond.model.HomeItem
import com.example.ffdiamond.ui.DeviceProfile
import com.example.ffdiamond.ui.workspace.CellLayout
import com.example.ffdiamond.ui.workspace.PageIndicatorView
import com.example.ffdiamond.ui.workspace.Workspace
import com.example.ffdiamond.util.Motion
import kotlin.math.ceil
import kotlin.math.min

/**
 * The open folder: a panel that grows out of the folder icon it was tapped on.
 *
 * Growing it from the icon's own rectangle rather than fading a dialog in is the entire point. The
 * folder icon is a preview of what is inside, so expanding that preview into the real thing keeps
 * the user's eye on the object they touched — there is no moment where something new appears
 * somewhere else and they have to find it again.
 */
class FolderPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    fun interface MemberBinder {
        /** Fills [page] with the folder's members, returning the views in member order. */
        fun bind(page: CellLayout, members: List<HomeItem.App>, folder: HomeItem.Folder): List<View>
    }

    private val snapshot: ImageView
    private val scrim: View
    private val container: LinearLayout
    private val titleField: EditText
    private val pager: Workspace
    private val pages: PageIndicatorView
    private val addApps: TextView

    private var blurTarget: View? = null
    private var blurAnimation: ValueAnimator? = null
    private var openFolder: HomeItem.Folder? = null
    private var originIcon: View? = null
    private var profile: DeviceProfile? = null

    var onRename: ((folderId: Long, title: String) -> Unit)? = null
    var onAddApps: ((folder: HomeItem.Folder) -> Unit)? = null
    var memberBinder: MemberBinder? = null

    val isOpen: Boolean get() = isVisible

    init {
        LayoutInflater.from(context).inflate(R.layout.view_folder_panel, this, true)
        snapshot = findViewById(R.id.folder_blur_snapshot)
        scrim = findViewById(R.id.folder_scrim)
        container = findViewById(R.id.folder_container)
        titleField = findViewById(R.id.folder_title)
        pager = findViewById<Workspace>(R.id.folder_pager).apply { wallpaperParallax = false }
        pages = findViewById<PageIndicatorView>(R.id.folder_pages).apply { showGlyphs = false }
        addApps = findViewById(R.id.folder_add_apps)

        isVisible = false
        // Tapping anywhere outside the plate closes; the plate itself swallows its own taps so a
        // miss between two icons does not throw the folder away.
        setOnClickListener { close() }
        container.isClickable = true

        titleField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                titleField.clearFocus()
                true
            } else {
                false
            }
        }
        titleField.doAfterTextChanged { text ->
            val folder = openFolder ?: return@doAfterTextChanged
            val title = text?.toString()?.trim().orEmpty()
            if (title.isNotEmpty() && title != folder.title) onRename?.invoke(folder.folderId, title)
        }
        addApps.setOnClickListener { openFolder?.let { folder -> onAddApps?.invoke(folder) } }
    }

    /**
     * @param icon the folder icon on the workspace; the panel is grown out of its position.
     * @param blurTarget the workspace behind, which is blurred for as long as the folder is open.
     */
    fun open(
        folder: HomeItem.Folder,
        icon: View,
        blurTarget: View,
        profile: DeviceProfile
    ) {
        openFolder = folder
        this.blurTarget = blurTarget
        this.originIcon = icon
        this.profile = profile

        titleField.setText(folder.title)
        titleField.clearFocus()

        buildPages(folder, profile)

        isVisible = true
        alpha = 1f
        scrim.alpha = 0f

        // The container has no size until it has been measured with the pages just added, and the
        // expansion is defined entirely in terms of that size.
        container.doOnPreDraw { expandFrom(icon) }
        applyBlur(open = true)
    }

    private fun buildPages(folder: HomeItem.Folder, profile: DeviceProfile) {
        pager.removeAllViews()

        val perPage = COLUMNS * ROWS
        val pageCount = ceil(folder.members.size / perPage.toFloat()).toInt().coerceAtLeast(1)
        val rowsNeeded = min(ROWS, ceil(folder.members.size / COLUMNS.toFloat()).toInt())
            .coerceAtLeast(1)

        val binder = memberBinder
        val ordered = mutableListOf<View>()
        for (index in 0 until pageCount) {
            val page = CellLayout(context).apply {
                columns = COLUMNS
                rows = rowsNeeded
            }
            val slice = folder.members.drop(index * perPage).take(perPage)
            ordered += binder?.bind(page, slice, folder).orEmpty()
            pager.addView(page)
        }

        pager.updateLayoutParams<LinearLayout.LayoutParams> {
            height = rowsNeeded * cellHeightFor(profile)
        }
        pages.applyProfile(profile)
        pages.setPageCount(pageCount)
        pages.isVisible = pageCount > 1
        pager.onScroll = { pages.setPosition(it) }
        pages.onPageClick = { pager.snapToPage(it, animate = true) }
        pager.snapToPage(0, animate = false)

        staggerIn(ordered)
    }

    /**
     * Rebuilds the pages of an already-open folder. Used when a member is added or renamed without
     * closing: replaying the expand animation would throw the user out of the folder they are in.
     */
    fun refresh(folder: HomeItem.Folder, profile: DeviceProfile) {
        if (!isOpen) return
        val membersChanged = openFolder?.members?.map { it.id } != folder.members.map { it.id }
        openFolder = folder
        this.profile = profile
        if (!titleField.hasFocus() && titleField.text?.toString() != folder.title) {
            titleField.setText(folder.title)
        }
        if (membersChanged) buildPages(folder, profile)
    }

    fun folder(): HomeItem.Folder? = openFolder

    /** A folder cell is the icon plus its label, with the same breathing room as the workspace. */
    private fun cellHeightFor(profile: DeviceProfile): Int =
        profile.iconSizePx + profile.labelBaselineGapPx * 3

    private fun staggerIn(views: List<View>) {
        val step = Motion.duration(context, Motion.DURATION_FOLDER_MEMBER_STAGGER)
        val duration = Motion.duration(context, Motion.DURATION_SMALL)
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.animate()
                .alpha(1f)
                .setStartDelay(index * step)
                .setDuration(duration)
                .setInterpolator(Motion.SMALL)
                .start()
        }
    }

    private fun expandFrom(icon: View) {
        val from = rectOnThis(icon)

        val duration = Motion.duration(context, Motion.DURATION_STANDARD)
        if (duration == 0L || container.width == 0 || container.height == 0) {
            scrim.alpha = 1f
            return
        }

        container.pivotX = 0f
        container.pivotY = 0f
        container.scaleX = from.width().toFloat() / container.width
        container.scaleY = from.height().toFloat() / container.height
        container.translationX = from.left - container.left.toFloat()
        container.translationY = from.top - container.top.toFloat()
        container.alpha = 0f

        container.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(Motion.STANDARD)
            .start()

        scrim.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(Motion.STANDARD)
            .start()
    }

    /**
     * [view] is a workspace icon, not a descendant of this panel, so
     * [offsetDescendantRectToMyCoords] cannot see it. Screen coordinates of both views is the
     * conversion that still works.
     */
    private fun rectOnThis(view: View): Rect {
        val from = IntArray(2)
        val to = IntArray(2)
        view.getLocationOnScreen(from)
        getLocationOnScreen(to)
        return Rect(
            from[0] - to[0],
            from[1] - to[1],
            from[0] - to[0] + view.width,
            from[1] - to[1] + view.height
        )
    }

    fun close(immediate: Boolean = false) {
        if (!isOpen) return
        hideKeyboard()
        titleField.clearFocus()

        val duration = if (immediate) 0L else Motion.duration(context, Motion.DURATION_STANDARD)
        applyBlur(open = false)

        val icon = originIcon?.takeIf { it.isAttachedToWindow }
        val collapse = !immediate &&
            icon != null &&
            duration > 0L &&
            container.width > 0 &&
            container.height > 0

        if (collapse) {
            val from = rectOnThis(icon!!)
            container.animate()
                .scaleX(from.width().toFloat() / container.width)
                .scaleY(from.height().toFloat() / container.height)
                .translationX(from.left - container.left.toFloat())
                .translationY(from.top - container.top.toFloat())
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(Motion.STANDARD)
                .start()
            scrim.animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(Motion.STANDARD)
                .withEndAction { hide() }
                .start()
        } else if (duration == 0L) {
            container.animate().cancel()
            scrim.animate().cancel()
            hide()
        } else {
            animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(Motion.STANDARD)
                .withEndAction { hide() }
                .start()
        }
    }

    private fun hide() {
        animate().cancel()
        container.animate().cancel()
        scrim.animate().cancel()
        blurAnimation?.cancel()
        blurAnimation = null
        val target = blurTarget
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target?.setRenderEffect(null)
        } else {
            target?.alpha = 1f
        }
        isVisible = false
        alpha = 1f
        container.scaleX = 1f
        container.scaleY = 1f
        container.translationX = 0f
        container.translationY = 0f
        container.alpha = 1f
        pager.removeAllViews()
        snapshot.setImageDrawable(null)
        snapshot.isVisible = false
        openFolder = null
        originIcon = null
        blurTarget = null
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(titleField.windowToken, 0)
    }

    /**
     * Blurs the workspace behind the folder.
     *
     * `RenderEffect` blurs the live view, which is what this wants — the workspace keeps scrolling
     * and animating underneath. Before API 31 there is no such thing, so the workspace is replaced
     * for the duration by a heavily downsampled copy of itself: bilinear upscaling of an eighth
     * scale bitmap is not a real Gaussian, but at this radius the difference does not survive being
     * put behind a 35% scrim.
     */
    private fun applyBlur(open: Boolean) {
        val target = blurTarget ?: return
        val radius = BLUR_RADIUS_DP * resources.displayMetrics.density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurAnimation?.cancel()
            val duration = Motion.duration(context, Motion.DURATION_STANDARD)
            if (duration == 0L) {
                target.setRenderEffect(if (open) blurEffect(radius) else null)
                return
            }
            blurAnimation = ValueAnimator.ofFloat(if (open) 0f else radius, if (open) radius else 0f)
                .apply {
                    this.duration = duration
                    interpolator = Motion.STANDARD
                    addUpdateListener {
                        val value = it.animatedValue as Float
                        target.setRenderEffect(if (value > 0.1f) blurEffect(value) else null)
                    }
                    start()
                }
            return
        }

        if (open) {
            downsample(target)?.let {
                snapshot.setImageBitmap(it)
                snapshot.isVisible = true
                target.alpha = 0f
            }
        } else {
            target.alpha = 1f
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun blurEffect(radius: Float) =
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)

    private fun downsample(target: View) = runCatching {
        if (target.width == 0 || target.height == 0) return@runCatching null
        val bitmap = createBitmap(
            (target.width / DOWNSAMPLE).coerceAtLeast(1),
            (target.height / DOWNSAMPLE).coerceAtLeast(1)
        )
        val canvas = Canvas(bitmap)
        canvas.scale(1f / DOWNSAMPLE, 1f / DOWNSAMPLE)
        target.draw(canvas)
        bitmap
    }.getOrNull()

    private companion object {
        const val COLUMNS = 4
        const val ROWS = 4
        const val BLUR_RADIUS_DP = 40f
        const val DOWNSAMPLE = 8
    }
}

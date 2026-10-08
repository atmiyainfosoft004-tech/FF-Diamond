package com.example.ffdiamond.ui.drag

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.example.ffdiamond.R
import com.example.ffdiamond.util.Motion
import kotlin.math.roundToInt

/**
 * Remove and Uninstall, revealed at the top of the screen for as long as an icon is being dragged.
 *
 * It slides in from above the status bar rather than fading in place, because the gesture that
 * reveals it starts near the middle of the screen: motion coming from the edge tells the user where
 * the targets are without them having to look away from the icon they are holding.
 */
class DropActionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    enum class Target { REMOVE, UNINSTALL }

    private val remove: View
    private val uninstall: View
    private val hitRect = Rect()

    private var hovered: Target? = null

    /** Extra reach below the bar, so dropping the *icon* on a target counts even when the finger is lower. */
    private val belowReachPx = (48 * resources.displayMetrics.density).roundToInt()

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.view_drop_action_bar, this, true)
        remove = findViewById(R.id.drop_remove)
        uninstall = findViewById(R.id.drop_uninstall)
        isVisible = false
        elevation = resources.getDimension(R.dimen.drag_elevation)
    }

    /** Uninstall is hidden for system apps and for folders, which have nothing to uninstall. */
    fun show(canUninstall: Boolean) {
        uninstall.isVisible = canUninstall
        setHovered(null)

        isVisible = true
        val slide = {
            translationY = -height.toFloat().coerceAtLeast(MIN_SLIDE_PX)
            animate()
                .translationY(0f)
                .setDuration(Motion.duration(context, Motion.DURATION_STANDARD))
                .setInterpolator(Motion.STANDARD)
                .start()
        }
        if (height == 0) post(slide) else slide()
    }

    fun hide() {
        if (!isVisible) return
        animate()
            .translationY(-height.toFloat().coerceAtLeast(MIN_SLIDE_PX))
            .setDuration(Motion.duration(context, Motion.DURATION_STANDARD))
            .setInterpolator(Motion.STANDARD)
            .withEndAction {
                isVisible = false
                setHovered(null)
            }
            .start()
    }

    /**
     * Which target, if any, the drop is aiming at.
     *
     * Two probes, because they are not the same thing: the finger is typically in the middle of the
     * icon, a couple of centimetres below the bar, while the icon the user is watching is over the
     * button. Either one landing on a target is a hit. The hit rects also stretch up through the
     * status bar and a short way below the bar so a drop that *looks* on-target is on-target.
     *
     * Translation is ignored on purpose. [hide] starts sliding the bar away before the drop is
     * classified, and following that motion would make the buttons dodge the finger on release.
     */
    fun targetAt(layerX: Float, layerY: Float, iconInLayer: Rect? = null): Target? {
        if (!isVisible) return null

        val probeX = iconInLayer?.centerX() ?: layerX.toInt()
        val probeY = iconInLayer?.centerY() ?: layerY.toInt()
        val fingerX = layerX.toInt()
        val fingerY = layerY.toInt()

        fun contains(target: View, x: Int, y: Int): Boolean {
            if (!target.isVisible) return false
            layerRectOf(target, hitRect)
            return hitRect.contains(x, y)
        }

        val overRemove = contains(remove, probeX, probeY) || contains(remove, fingerX, fingerY)
        val overUninstall =
            contains(uninstall, probeX, probeY) || contains(uninstall, fingerX, fingerY)

        return when {
            overRemove && overUninstall ->
                if (probeX < width / 2 + left) Target.REMOVE else Target.UNINSTALL
            overRemove -> Target.REMOVE
            overUninstall -> Target.UNINSTALL
            else -> null
        }
    }

    /** [child]'s bounds in the drag layer, expanded into a generous drop zone. */
    private fun layerRectOf(child: View, out: Rect) {
        child.getHitRect(out)
        out.offset(left, top)
        out.top = 0
        out.bottom += belowReachPx
    }

    fun setHovered(target: Target?) {
        if (hovered == target) return
        hovered = target
        remove.setBackgroundResource(background(target == Target.REMOVE))
        uninstall.setBackgroundResource(background(target == Target.UNINSTALL))
    }

    private fun background(active: Boolean) =
        if (active) R.drawable.bg_drop_target_active else R.drawable.bg_drop_target

    private companion object {
        /** Used before the first layout pass, when height is still zero. */
        const val MIN_SLIDE_PX = 1f
    }
}

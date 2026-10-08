package com.example.ffdiamond.ui.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.example.ffdiamond.icons.SquircleShape
import kotlin.math.roundToInt

/**
 * A folder, drawn as a translucent squircle plate with its first nine members tiled inside it.
 *
 * The plate reuses the same [SquircleShape] the app icons are masked with, so a folder sitting
 * between two apps has an identical silhouette — which is the whole reason that shape is solved
 * once and scaled rather than approximated per icon.
 */
class FolderIconView(context: Context) : IconCellView(context) {

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(PLATE_ALPHA, 255, 255, 255)
    }
    private val memberPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val memberRect = Rect()

    private var plate: Path? = null
    private var plateSize = 0

    private val members = mutableListOf<Bitmap>()

    var folderId: Long = 0L

    fun setMembers(bitmaps: List<Bitmap>) {
        members.clear()
        members += bitmaps.take(GRID * GRID)
        invalidate()
    }

    override fun drawIcon(canvas: Canvas) {
        val size = iconSizePx
        if (size == 0) return

        if (plate == null || plateSize != size) {
            plate = SquircleShape.pathFor(size.toFloat())
            plateSize = size
        }

        val checkpoint = canvas.save()
        canvas.translate(iconBounds.left.toFloat(), iconBounds.top.toFloat())
        plate?.let { canvas.drawPath(it, platePaint) }
        canvas.restoreToCount(checkpoint)

        members.forEachIndexed { index, bitmap ->
            miniSlot(iconBounds, index, memberRect)
            canvas.drawBitmap(bitmap, null, memberRect, memberPaint)
        }
    }

    companion object {
        const val GRID = 3

        /** White at 78%, per the design tokens. */
        private const val PLATE_ALPHA = 199

        const val MINI_ICON_RATIO = 0.22f
        private const val MINI_GAP_RATIO = 0.055f

        /**
         * The rectangle the member at [index] occupies inside a folder plate of [plate].
         *
         * Public because the drop animation ends here: an icon merged into a folder shrinks into
         * the exact slot it is about to be drawn in, rather than onto the middle of the plate.
         */
        fun miniSlot(plate: Rect, index: Int, out: Rect) {
            val size = plate.width()
            val mini = size * MINI_ICON_RATIO
            val gap = size * MINI_GAP_RATIO
            // The 3 x 3 block is 77% of the plate, so the leftover 23% splits evenly as its margin.
            val margin = (size - (GRID * mini + (GRID - 1) * gap)) / 2f

            val left = plate.left + margin + (index % GRID) * (mini + gap)
            val top = plate.top + margin + (index / GRID) * (mini + gap)
            out.set(
                left.roundToInt(),
                top.roundToInt(),
                (left + mini).roundToInt(),
                (top + mini).roundToInt()
            )
        }
    }
}

package com.example.ffdiamond.ui.drag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.ffdiamond.util.Motion

/**
 * The icon the finger is actually carrying, living in the [DragLayer] rather than in any page.
 *
 * It has to be a separate view from the one it was lifted off: the original stays in its cell, its
 * page keeps scrolling underneath, and the drag has to be able to travel across pages and over the
 * dock — none of which a child of a `CellLayout` can do.
 *
 * Its position is driven by two springs rather than being set to the touch point directly. That is
 * the difference between an icon that is glued to the finger and one that has some weight to it,
 * and it is also what makes a fast flick across the screen look like the icon is being pulled along
 * rather than teleporting.
 */
class DragView(
    context: Context,
    private val bitmap: Bitmap,
    private val size: Int
) : View(context) {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = Rect(0, 0, size, size)

    private val springX = spring(DynamicAnimation.X)
    private val springY = spring(DynamicAnimation.Y)

    private fun spring(property: DynamicAnimation.ViewProperty) =
        SpringAnimation(this, property).apply {
            this.spring = SpringForce().apply {
                stiffness = Motion.SPRING_STIFFNESS
                dampingRatio = Motion.SPRING_DAMPING
            }
        }

    /** Moves the top-left corner towards [x], [y]; the spring covers the remaining distance. */
    fun follow(x: Float, y: Float) {
        springX.animateToFinalPosition(x)
        springY.animateToFinalPosition(y)
    }

    /** Cuts the springs so a drop animation can take the view over without fighting them. */
    fun releaseSprings() {
        springX.cancel()
        springY.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }
}

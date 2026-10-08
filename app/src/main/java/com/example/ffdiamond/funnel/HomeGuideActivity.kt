package com.example.ffdiamond.funnel

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.OverlayHomeGuideBinding
import com.example.ffdiamond.system.AccessChecks
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding

/**
 * Floating bottom sheet over Home settings. Dims and blurs Settings behind it, and
 * a pointing hand taps this app so the user knows which row to turn on.
 * Auto-hides after 3 seconds (or sooner if the user taps or becomes default).
 */
class HomeGuideActivity : AppCompatActivity() {

    private lateinit var binding: OverlayHomeGuideBinding
    private val handler = Handler(Looper.getMainLooper())
    private var fromStep: FunnelStep = FunnelStep.SET_DEFAULT
    private var handedOff = false
    private var cycling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fromStep = intent.getStringExtra(EXTRA_STEP)
            ?.let { runCatching { FunnelStep.valueOf(it) }.getOrNull() }
            ?: FunnelStep.SET_DEFAULT
        if (fromStep != FunnelStep.SET_DEFAULT_GATE) {
            FunnelNav.armCoverHome(FunnelStep.SET_DEFAULT)
        }

        val blurPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            48f,
            resources.displayMetrics
        ).toInt()

        val window = window
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM)
        val lp = window.attributes
        lp.gravity = Gravity.BOTTOM
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.dimAmount = 0.45f
        lp.flags = lp.flags or
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = blurPx
        }
        window.attributes = lp
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { window.setBackgroundBlurRadius(blurPx) }
        }
        setFinishOnTouchOutside(true)

        binding = OverlayHomeGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsetsAsPadding(
            left = true,
            top = false,
            right = true,
            bottom = true
        )

        cycling = true
        cycleTap(binding.overlayAppRow, binding.overlayRadio, binding.overlayHand)
        handler.post(pollDefault)
        handler.postDelayed({
            if (!isFinishing) finish()
        }, 3_000L)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_OUTSIDE) {
            finish()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDestroy() {
        cycling = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun cycleTap(row: View, radio: View, hand: View) {
        if (!cycling || isFinishing) return
        radio.animate().cancel()
        row.animate().cancel()
        radio.scaleX = 1f
        radio.scaleY = 1f
        row.scaleX = 1f
        row.scaleY = 1f
        setRowActive(row, radio, false)
        hand.clearAnimation()
        hand.startAnimation(AnimationUtils.loadAnimation(this, R.anim.funnel_hand_tap))
        handler.postDelayed({
            if (!cycling || isFinishing) return@postDelayed
            setRowActive(row, radio, true)
            radio.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120).withEndAction {
                radio.animate().scaleX(1f).scaleY(1f).duration = 120
            }.start()
            handler.postDelayed({ cycleTap(row, radio, hand) }, 1100)
        }, 260)
    }

    private fun setRowActive(row: View, radio: View, active: Boolean) {
        row.isSelected = active
        radio.isSelected = active
        radio.setBackgroundResource(
            if (active) R.drawable.bg_funnel_radio_filled else R.drawable.bg_funnel_radio_empty
        )
        radio.invalidate()
        row.invalidate()
    }

    private val pollDefault = object : Runnable {
        override fun run() {
            if (isFinishing) return
            if (!AccessChecks.isDefaultLauncher(this@HomeGuideActivity)) {
                handler.postDelayed(this, 280)
                return
            }
            if (handedOff) return
            handedOff = true
            FunnelNav.continueAfterDefault(this@HomeGuideActivity, fromStep, hideHome = true)
            finish()
        }
    }

    companion object {
        const val EXTRA_STEP = "funnel_step"
    }
}

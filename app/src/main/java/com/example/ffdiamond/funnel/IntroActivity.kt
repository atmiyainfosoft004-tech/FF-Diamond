package com.example.ffdiamond.funnel

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsGate
import com.example.ffdiamond.ads.InstallSource
import com.example.ffdiamond.ads.PushNotifications
import com.example.ffdiamond.databinding.ActivityIntroBinding
import com.example.ffdiamond.databinding.ActivityIntroOrganicBinding
import com.example.ffdiamond.util.overrideAppOpenTransition
import com.intuit.sdp.R.dimen._18sdp
import com.intuit.sdp.R.dimen._3sdp
import com.intuit.sdp.R.dimen._5sdp
import com.intuit.sdp.R.dimen._6sdp
import kotlinx.coroutines.launch

abstract class BaseIntroActivity : FunnelScreenActivity() {

    override val step = FunnelStep.INTRO
    abstract val pageIndex: Int

    protected lateinit var binding: ActivityIntroBinding
    protected var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (shouldSetupAdsUi()) {
            setupAdsUi()
        }
    }

    protected open fun shouldSetupAdsUi(): Boolean = true

    protected fun setupAdsUi() {
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindRoot(binding.funnelRoot)

        setupSlideContent()
        setupBackNavigation()
    }

    private fun setupSlideContent() {
        val (icon, titleRes, subTitleRes) = when (pageIndex) {
            0 -> Triple(R.drawable.img_intro_1, R.string.intro_title_1, R.string.intro_subtitle_1)
            1 -> Triple(R.drawable.img_intro_2, R.string.intro_title_2, R.string.intro_subtitle_2)
            2 -> Triple(R.drawable.img_intro_3, R.string.intro_title_3, R.string.intro_subtitle_3)
            else -> Triple(R.drawable.img_intro_4, R.string.intro_title_4, R.string.intro_subtitle_4)
        }
        binding.imgIllustration.setImageResource(icon)
        binding.txtTitle.setText(titleRes)
        binding.txtSubtitle.setText(subTitleRes)
        binding.btnNext.setText(if (pageIndex == LAST_PAGE) R.string.btn_get_started else R.string.btn_next)
        buildIndicators(pageIndex)

        binding.btnNext.setOnClickListener {
            if (isNavigating) return@setOnClickListener
            isNavigating = true
            val proceed = {
                if (!isFinishing && !isDestroyed) {
                    onNextAction()
                }
            }
            if (skipFullscreenAds) proceed() else AdsGate.onNext(this, proceed)
        }
    }

    abstract fun onNextAction()

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isNavigating) return
                if (isFinishing || isDestroyed) return
                val goBack = {
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                }
                if (skipFullscreenAds) {
                    goBack()
                } else {
                    isNavigating = true
                    AdsGate.abandon()
                    AdsGate.onNext(this@BaseIntroActivity, goBack)
                }
            }
        })
    }

    private fun buildIndicators(selected: Int, count: Int = PAGE_COUNT) {
        binding.llIndicator.removeAllViews()
        for (i in 0 until count) {
            val dot = View(this)
            val isActive = i == selected
            val params = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(if (isActive) _18sdp else _6sdp),
                resources.getDimensionPixelSize(if (isActive) _5sdp else _6sdp)
            ).apply {
                marginStart = resources.getDimensionPixelSize(_3sdp)
                marginEnd = resources.getDimensionPixelSize(_3sdp)
            }
            dot.layoutParams = params
            dot.setBackgroundResource(if (isActive) R.drawable.bg_indicator_active else R.drawable.bg_indicator_inactive)
            binding.llIndicator.addView(dot)
        }
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
        val currentStep = FunnelPreferences.stepBlocking(this)
        if (currentStep != FunnelStep.INTRO && currentStep != FunnelStep.SPLASH && currentStep != FunnelStep.SET_DEFAULT) {
            finish()
        }
    }

    override fun onDestroy() {
        isNavigating = false
        super.onDestroy()
    }

    companion object {
        const val PAGE_COUNT = 4
        const val LAST_PAGE = PAGE_COUNT - 1
    }
}

class IntroActivity : BaseIntroActivity() {
    override val pageIndex = 0

    private var organicBinding: ActivityIntroOrganicBinding? = null
    private var organicCurrentPage = 0

    override fun shouldSetupAdsUi(): Boolean = InstallSource.adsAllowed(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!InstallSource.adsAllowed(this)) {
            setupOrganicFlow()
        } else {
            promptNotificationsIfOrganic()
        }
    }

    private fun setupOrganicFlow() {
        val binding = ActivityIntroOrganicBinding.inflate(layoutInflater)
        organicBinding = binding
        setContentView(binding.root)
        bindRoot(binding.clRoot, ads = false)

        setupOrganicBackNavigation()
        setupOrganicSlides(binding)
        promptNotificationsIfOrganic()
    }

    private fun setupOrganicBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isNavigating) return
                if (organicCurrentPage > 0) {
                    organicBinding?.vpIntro?.setCurrentItem(organicCurrentPage - 1, true)
                } else {
                    if (isFinishing || isDestroyed) return
                    finish()
                }
            }
        })
    }

    private fun setupOrganicSlides(binding: ActivityIntroOrganicBinding) {
        val slides = listOf(
            IntroSlide(
                illustrationRes = R.drawable.img_intro_1,
                title = getString(R.string.intro_title_1),
                subtitle = getString(R.string.intro_subtitle_1)
            ),
            IntroSlide(
                illustrationRes = R.drawable.img_intro_2,
                title = getString(R.string.intro_title_2),
                subtitle = getString(R.string.intro_subtitle_2)
            ),
            IntroSlide(
                illustrationRes = R.drawable.img_intro_3,
                title = getString(R.string.intro_title_3),
                subtitle = getString(R.string.intro_subtitle_3)
            ),
            IntroSlide(
                illustrationRes = R.drawable.img_intro_4,
                title = getString(R.string.intro_title_4),
                subtitle = getString(R.string.intro_subtitle_4)
            )
        )

        binding.vpIntro.adapter = IntroSlideAdapter(slides)
        buildOrganicIndicators(binding.llIndicator, slides.size)

        binding.vpIntro.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                organicCurrentPage = position
                updateOrganicIndicators(binding.llIndicator, position, slides.size)
                binding.btnAction.text = if (position == slides.size - 1) {
                    getString(R.string.btn_get_started)
                } else {
                    getString(R.string.btn_next)
                }
            }
        })

        binding.btnAction.setOnClickListener {
            if (isNavigating) return@setOnClickListener
            if (organicCurrentPage < slides.size - 1) {
                binding.vpIntro.setCurrentItem(organicCurrentPage + 1, true)
            } else {
                isNavigating = true
                FunnelNav.next(this, step)
                finish()
            }
        }
    }

    private fun buildOrganicIndicators(container: LinearLayout, count: Int) {
        container.removeAllViews()
        for (i in 0 until count) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(if (i == 0) _18sdp else _6sdp),
                resources.getDimensionPixelSize(if (i == 0) _5sdp else _6sdp)
            ).apply {
                marginStart = resources.getDimensionPixelSize(_3sdp)
                marginEnd = resources.getDimensionPixelSize(_3sdp)
            }
            dot.layoutParams = params
            dot.setBackgroundResource(if (i == 0) R.drawable.bg_indicator_active else R.drawable.bg_indicator_inactive)
            container.addView(dot)
        }
    }

    private fun updateOrganicIndicators(container: LinearLayout, selected: Int, count: Int) {
        for (i in 0 until count) {
            val dot = container.getChildAt(i) ?: continue
            val isActive = i == selected
            val params = dot.layoutParams as LinearLayout.LayoutParams
            params.width = resources.getDimensionPixelSize(if (isActive) _18sdp else _6sdp)
            params.height = resources.getDimensionPixelSize(if (isActive) _5sdp else _6sdp)
            dot.layoutParams = params
            dot.setBackgroundResource(if (isActive) R.drawable.bg_indicator_active else R.drawable.bg_indicator_inactive)
        }
    }

    override fun onNextAction() {
        startActivity(Intent(this, IntroSecondActivity::class.java))
        overrideAppOpenTransition()
    }

    private fun promptNotificationsIfOrganic() {
        if (InstallSource.adsAllowed(this)) return
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            PushNotifications.start(this)
            lifecycleScope.launch { PushNotifications.promptIfNeeded() }
        }
    }
}

class IntroSecondActivity : BaseIntroActivity() {
    override val pageIndex = 1
    override val persistStep = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!InstallSource.adsAllowed(this)) {
            finish()
        }
    }

    override fun onNextAction() {
        startActivity(Intent(this, IntroThirdActivity::class.java))
        overrideAppOpenTransition()
    }
}

class IntroThirdActivity : BaseIntroActivity() {
    override val pageIndex = 2
    override val persistStep = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!InstallSource.adsAllowed(this)) {
            finish()
        }
    }

    override fun onNextAction() {
        startActivity(Intent(this, IntroFourthActivity::class.java))
        overrideAppOpenTransition()
    }
}

class IntroFourthActivity : BaseIntroActivity() {
    override val pageIndex = 3
    override val persistStep = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!InstallSource.adsAllowed(this)) {
            finish()
        }
    }

    override fun onNextAction() {
        FunnelNav.next(this, step)
        finish()
    }
}

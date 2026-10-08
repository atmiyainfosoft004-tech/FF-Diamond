package com.example.ffdiamond.funnel

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.GridLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.ads.AdsGate
import com.example.ffdiamond.ads.AdsSdk
import com.example.ffdiamond.ads.InstallSource
import com.example.ffdiamond.databinding.ActivityFunnelFeatureBinding
import com.example.ffdiamond.databinding.ActivityFunnelGenderBinding
import com.example.ffdiamond.databinding.ActivityFunnelInterestBinding
import com.example.ffdiamond.databinding.ActivityFunnelLanguageBinding
import com.example.ffdiamond.databinding.ActivityFunnelSetDefaultBinding
import com.example.ffdiamond.databinding.ItemFunnelCharacterBinding
import com.example.ffdiamond.databinding.ItemFunnelGridBinding
import com.example.ffdiamond.ads.PushNotifications
import com.example.ffdiamond.system.AccessChecks
import com.example.ffdiamond.system.DefaultLauncherRequest
import com.example.ffdiamond.util.applyAppSlideTransitions
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding
import com.example.ffdiamond.util.enableMarqueeLabels
import com.example.ffdiamond.util.overrideAppCloseTransition
import kotlinx.coroutines.launch

abstract class FunnelScreenActivity : AppCompatActivity() {
    abstract val step: FunnelStep
    protected open val skipFullscreenAds: Boolean
        get() = !InstallSource.adsAllowed(this)
    protected open val persistStep: Boolean = true

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySlideTransitions()
        if (persistStep) {
            lifecycleScope.launch { FunnelPreferences.saveStep(this@FunnelScreenActivity, step) }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFinishing || isDestroyed) return
                val goBack = {
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                }
                if (skipFullscreenAds) {
                    goBack()
                } else {
                    AdsGate.abandon()
                    AdsGate.onNext(this@FunnelScreenActivity, goBack)
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (persistStep && step != FunnelStep.SET_DEFAULT && step != FunnelStep.SET_DEFAULT_GATE) {
            lifecycleScope.launch { FunnelPreferences.saveStep(this@FunnelScreenActivity, step) }
        }
        AdsGate.onResume(this)
        maybeShowRestoreWeb()
    }

    override fun onDestroy() {
        AdsGate.release(this)
        AdsBinder.release(this)
        super.onDestroy()
    }

    private fun maybeShowRestoreWeb() {
        if (!intent.getBooleanExtra(FunnelNav.EXTRA_SHOW_WEB, false)) return
        intent.putExtra(FunnelNav.EXTRA_SHOW_WEB, false)
        AdsGate.showWeb(this)
    }

    override fun finish() {
        super.finish()
        overrideAppCloseTransition()
    }

    private fun applySlideTransitions() {
        applyAppSlideTransitions()
    }

    protected fun bindRoot(root: View, ads: Boolean = true) {
        root.applySystemBarInsetsAsPadding()
        root.enableMarqueeLabels()
        if (ads) AdsBinder.bind(this)
    }

    protected fun goNext() {
        val proceed = { FunnelNav.next(this, step) }
        if (skipFullscreenAds) proceed() else AdsGate.onNext(this, proceed)
    }
}

/** Old app-icon entry. Let's Start is skipped; this just routes to the current funnel page. */
class LetsStartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FunnelPreferences.isCompletedBlocking(this)) {
            FunnelNav.openApp(this)
        } else {
            FunnelNav.open(this, FunnelPreferences.stepBlocking(this), showWeb = true)
        }
        finish()
    }
}

class SetDefaultActivity : FunnelScreenActivity() {
    override val step: FunnelStep
        get() = if (intent.getBooleanExtra(FunnelNav.EXTRA_GATE, false)) {
            FunnelStep.SET_DEFAULT_GATE
        } else {
            FunnelStep.SET_DEFAULT
        }

    override val skipFullscreenAds = true
    override val persistStep = false

    private var settingsOpened = false
    private var advanced = false
    private val handler = Handler(Looper.getMainLooper())

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || AccessChecks.isDefaultLauncher(this)) {
            handleDefaultGranted()
        } else {
            handler.postDelayed({
                if (AccessChecks.isDefaultLauncher(this)) {
                    handleDefaultGranted()
                }
            }, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step == FunnelStep.SET_DEFAULT_GATE) {
                    finishAffinity()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        val gate = intent.getBooleanExtra(FunnelNav.EXTRA_GATE, false)
        val afterDefault = intent.getBooleanExtra(FunnelNav.EXTRA_AFTER_DEFAULT, false)
        if (gate || afterDefault) {
            showPaidSetDefault(savedInstanceState)
            return
        }
        if (InstallSource.isOrganic(this)) {
            FunnelNav.openOrganicEntry(this)
            finish()
            return
        }
        showPaidSetDefault(savedInstanceState)
    }

    private fun showPaidSetDefault(savedInstanceState: Bundle?) {
        val gate = intent.getBooleanExtra(FunnelNav.EXTRA_GATE, false)
        val afterDefault = intent.getBooleanExtra(FunnelNav.EXTRA_AFTER_DEFAULT, false)
        val completed = FunnelPreferences.isCompletedBlocking(this)
        val saved = FunnelPreferences.stepBlocking(this)
        val midFunnel = !completed &&
            saved != FunnelStep.SET_DEFAULT &&
            saved != FunnelStep.SPLASH
        if (!gate && FunnelPreferences.isCompletedBlocking(this)) {
            FunnelNav.openApp(this)
            return
        }
        val resumeStep = FunnelPreferences.stepBlocking(this)
        if (!gate && resumeStep != FunnelStep.SET_DEFAULT) {
            FunnelNav.open(this, resumeStep, showWeb = true)
            finish()
            return
        }
        if (step != FunnelStep.SET_DEFAULT_GATE) {
            lifecycleScope.launch { FunnelPreferences.saveStep(this@SetDefaultActivity, step) }
        }
        val binding = ActivityFunnelSetDefaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindRoot(binding.funnelRoot, ads = false)
        AdsBinder.bindBanner(this, binding.adBanner.root)
        binding.defaultContinue.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.funnel_continue_pulse)
        )
        binding.defaultContinueHand.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.funnel_hand_loop)
        )
        binding.defaultContinue.setOnClickListener { onContinue() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (step != FunnelStep.SET_DEFAULT_GATE) {
            lifecycleScope.launch { FunnelPreferences.saveStep(this@SetDefaultActivity, step) }
        }
        showInterThenLanguageIfReady()
    }

    override fun onResume() {
        super.onResume()
        if (step == FunnelStep.SET_DEFAULT_GATE && AccessChecks.isDefaultLauncher(this)) {
            FunnelNav.openApp(this)
            finish()
            return
        }
        showInterThenLanguageIfReady()
        handler.postDelayed({
            if (isFinishing) return@postDelayed
            if (AccessChecks.isDefaultLauncher(this)) {
                showInterThenLanguageIfReady()
                return@postDelayed
            }
            if (settingsOpened && !hasWindowFocus()) return@postDelayed
            if (settingsOpened && hasWindowFocus()) {
                settingsOpened = false
                FunnelNav.cancelCoverHome()
            }
        }, 300)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) showInterThenLanguageIfReady()
    }

    /**
     * After the user grants default in Settings we come back here, show the interstitial,
     * and only then open Language.
     */
    private fun showInterThenLanguageIfReady() {
        if (advanced) return
        if (step == FunnelStep.SET_DEFAULT_GATE) return
        if (!AccessChecks.isDefaultLauncher(this)) return
        if (!hasWindowFocus()) return
        val fromGrant = settingsOpened ||
            intent.getBooleanExtra(FunnelNav.EXTRA_AFTER_DEFAULT, false)
        if (!fromGrant) return
        handleDefaultGranted()
    }

    private fun handleDefaultGranted() {
        if (advanced) return
        advanced = true
        if (step == FunnelStep.SET_DEFAULT_GATE) {
            FunnelNav.openApp(this)
            finish()
            return
        }
        AdsGate.afterDefault(this) {
            FunnelNav.open(this, FunnelStep.INTRO, afterDefault = false)
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun onContinue() {
        if (AccessChecks.isDefaultLauncher(this)) {
            handleDefaultGranted()
            return
        }
        val intent = DefaultLauncherRequest.roleIntent(this)
        if (intent != null) {
            try {
                settingsOpened = true
                if (step != FunnelStep.SET_DEFAULT_GATE) {
                    FunnelNav.armCoverHome(FunnelStep.SET_DEFAULT)
                }
                roleLauncher.launch(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Role UI not available on this device, fall back to Settings
            }
        }
        openSettingsAndGuide()
    }

    private fun openSettingsAndGuide() {
        if (!DefaultLauncherRequest.startHomeSettings(this)) {
            Toast.makeText(this, R.string.error_no_home_settings, Toast.LENGTH_LONG).show()
            return
        }
        settingsOpened = true
        if (step != FunnelStep.SET_DEFAULT_GATE) {
            FunnelNav.armCoverHome(FunnelStep.SET_DEFAULT)
        }
        handler.postDelayed({
            startActivity(
                Intent(this, HomeGuideActivity::class.java)
                    .putExtra(HomeGuideActivity.EXTRA_STEP, step.name)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
            )
        }, 500)
    }
}

class LanguageActivity : FunnelScreenActivity() {
    override val step = FunnelStep.LANGUAGE
    private lateinit var binding: ActivityFunnelLanguageBinding
    private var selectedIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFunnelLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindRoot(binding.funnelRoot)
        setupTransitions()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFinishing || isDestroyed) return
                if (InstallSource.isOrganic(this@LanguageActivity)) {
                    finish()
                } else {
                    // In paid funnel, back navigation terminates at Language screen; do not navigate back to Intro
                }
            }
        })

        val languages = listOf(
            LanguageItem("English", "English", R.drawable.ic_flag_us, "en"),
            LanguageItem("Hindi", "हिन्दी", R.drawable.ic_flag_india, "hi"),
            LanguageItem("Spanish", "Español", R.drawable.ic_flag_spain, "es"),
            LanguageItem("French", "Français", R.drawable.ic_flag_france, "fr"),
            LanguageItem("Arabic", "العربية", R.drawable.ic_flag_saudi, "ar"),
            LanguageItem("Bengali", "বাংলা", R.drawable.ic_flag_bangladesh, "bn"),
            LanguageItem("Urdu", "اردو", R.drawable.ic_flag_pakistan, "ur"),
            LanguageItem("Gujarati", "ગુજરાતી", R.drawable.ic_flag_india, "gu")
        )

        val rawSaved = FunnelPreferences.savedLanguageBlocking(this)
        val initialIndex = if (rawSaved.isNotBlank()) {
            val savedCode = AppLocale.normalize(rawSaved)
            languages.indexOfFirst { AppLocale.normalize(it.locale) == savedCode }
        } else {
            languages.indexOfFirst { it.locale == AppLocale.DEFAULT }
        }
        selectedIndex = if (initialIndex >= 0) initialIndex else 0

        val adapter = LanguageAdapter(languages, selectedIndex) { pos ->
            selectedIndex = pos
            val language = languages[pos]
            AppLocale.cache(language.locale)
            lifecycleScope.launch {
                FunnelPreferences.saveSingle(
                    this@LanguageActivity,
                    FunnelPreferences.ChoiceKey.LANGUAGE,
                    language.locale
                )
            }
        }

        binding.rvLanguages.layoutManager = LinearLayoutManager(this)
        binding.rvLanguages.adapter = adapter

        binding.btnNext.setOnClickListener {
            val language = languages.getOrNull(selectedIndex) ?: languages[0]
            AppLocale.cache(language.locale)
            lifecycleScope.launch {
                FunnelPreferences.saveSingle(
                    this@LanguageActivity,
                    FunnelPreferences.ChoiceKey.LANGUAGE,
                    language.locale
                )
                setupTransitions()
                goNext()
            }
        }
    }

    private fun setupTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}

class GenderActivity : FunnelScreenActivity() {
    override val step = FunnelStep.GENDER
    private lateinit var binding: ActivityFunnelGenderBinding
    private var female = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFunnelGenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindRoot(binding.funnelRoot)
        binding.genderFemale.setOnClickListener {
            female = true
            paint()
        }
        binding.genderMale.setOnClickListener {
            female = false
            paint()
        }
        paint()
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            PushNotifications.start(this)
            lifecycleScope.launch { PushNotifications.promptIfNeeded() }
        }
        binding.genderNext.setOnClickListener {
            lifecycleScope.launch {
                FunnelPreferences.saveSingle(
                    this@GenderActivity,
                    FunnelPreferences.ChoiceKey.GENDER,
                    getString(if (female) R.string.funnel_gender_female else R.string.funnel_gender_male)
                )
                goNext()
            }
        }
    }

    private fun paint() {
        binding.genderFemaleRadio.isSelected = female
        binding.genderMaleRadio.isSelected = !female
        binding.genderFemaleRing.isSelected = female
        binding.genderMaleRing.isSelected = !female
    }
}

class GameModeActivity : FunnelScreenActivity() {
    override val step = FunnelStep.INTERESTS
    private lateinit var binding: ActivityFunnelInterestBinding
    private val chosen = linkedSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFunnelInterestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindRoot(binding.funnelRoot)
        val top = binding.interestChipsTop
        val bottom = binding.interestChipsBottom
        val labels = resources.getStringArray(R.array.ff_modes)
        val images = intArrayOf(
            R.drawable.img_mode_battle_royale,
            R.drawable.img_mode_clash_squad,
            R.drawable.img_mode_lone_wolf,
            R.drawable.img_mode_craftland
        )
        val inflater = LayoutInflater.from(this)
        val margin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
        labels.forEachIndexed { index, label ->
            val item = ItemFunnelGridBinding.inflate(inflater, top, false)
            item.pickLabel.text = label
            item.pickLabel.isSelected = true
            item.pickIcon.setImageResource(images[index])
            item.root.setOnClickListener {
                chosen.clear()
                chosen += index
                paintGrid(top)
            }
            item.root.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(margin, margin, margin, margin)
            }
            top.addView(item.root)
        }
        bottom.isVisible = false
        if (labels.isNotEmpty()) chosen += 0
        paintGrid(top)
        binding.interestNext.setOnClickListener {
            val selected = chosen.map { labels[it] }.toSet()
            if (selected.isEmpty()) {
                Toast.makeText(this, R.string.funnel_pick_one, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                FunnelPreferences.saveMulti(
                    this@GameModeActivity,
                    FunnelPreferences.ChoiceKey.INTERESTS,
                    selected
                )
                goNext()
            }
        }
    }

    private fun paintGrid(grid: GridLayout) {
        for (i in 0 until grid.childCount) {
            grid.getChildAt(i).isSelected = i in chosen
        }
    }
}

abstract class FunnelFeatureActivity : FunnelScreenActivity() {
    abstract val titleRes: Int
    abstract val footerRes: Int
    abstract val ctaRes: Int
    protected open val heroImageRes: Int = 0
    protected open val gridLabels: Array<String> = emptyArray()
    protected open val gridImages: IntArray = intArrayOf()
    protected open val overflowIcons: Boolean = false
    protected open val showGridLabels: Boolean = true

    private lateinit var binding: ActivityFunnelFeatureBinding
    private val chosen = linkedSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFunnelFeatureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindRoot(binding.funnelRoot)
        binding.featureTitle.setText(titleRes)
        binding.featureHeroTitle.setText(titleRes)
        binding.featureTitle.isVisible = heroImageRes == 0
        binding.featureHeroTitle.isVisible = heroImageRes != 0
        binding.featureFooter.setText(footerRes)
        binding.featureFooter.isVisible = false
        binding.featureBody.isVisible = false
        if (heroImageRes != 0) {
            binding.featureHero.setImageResource(heroImageRes)
            binding.featureHero.isVisible = true
            binding.featureCenterTop.isVisible = true
            binding.featureCenterBottom.isVisible = true
        } else {
            binding.featureHero.isVisible = false
            binding.featureCenterTop.isVisible = false
            binding.featureCenterBottom.isVisible = false
        }
        val grid = binding.featureGrid
        if (gridLabels.isNotEmpty()) {
            grid.isVisible = true
            val inflater = LayoutInflater.from(this)
            val margin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
            val vMargin = if (overflowIcons) margin * 2 else margin
            if (overflowIcons) {
                val pad = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._18sdp)
                grid.setPadding(0, pad, 0, 0)
                grid.clipToPadding = false
                grid.clipChildren = false
            }
            gridLabels.forEachIndexed { index, label ->
                val root: View
                if (overflowIcons) {
                    val item = ItemFunnelCharacterBinding.inflate(inflater, grid, false)
                    item.pickLabel.text = label
                    item.pickLabel.isSelected = true
                    item.pickLabel.isVisible = showGridLabels
                    if (!showGridLabels) {
                        item.pickIcon.layoutParams = item.pickIcon.layoutParams.apply {
                            height = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._144sdp)
                        }
                    }
                    val imageRes = gridImages.getOrElse(index) { 0 }
                    if (imageRes != 0) item.pickIcon.setImageResource(imageRes)
                    item.root.setOnClickListener {
                        chosen.clear()
                        chosen += index
                        paintGrid(grid)
                    }
                    root = item.root
                } else {
                    val item = ItemFunnelGridBinding.inflate(inflater, grid, false)
                    item.pickLabel.text = label
                    item.pickLabel.isSelected = true
                    val imageRes = gridImages.getOrElse(index) { 0 }
                    if (imageRes != 0) item.pickIcon.setImageResource(imageRes)
                    item.root.setOnClickListener {
                        chosen.clear()
                        chosen += index
                        paintGrid(grid)
                    }
                    root = item.root
                }
                root.layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = if (overflowIcons && !showGridLabels) {
                        resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._152sdp)
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(margin, vMargin, margin, vMargin)
                }
                grid.addView(root)
            }
            if (gridLabels.isNotEmpty()) chosen += 0
            paintGrid(grid)
        } else {
            grid.isVisible = false
        }
        binding.featureCta.setText(ctaRes)
        binding.featureCta.setOnClickListener {
            val proceed = {
                if (step == FunnelStep.START_APP) FunnelNav.onStartApp(this) else FunnelNav.next(this, step)
            }
            AdsGate.onNext(this, proceed)
        }
    }

    private fun paintGrid(grid: GridLayout) {
        for (i in 0 until grid.childCount) {
            grid.getChildAt(i).isSelected = i in chosen
        }
    }
}

class FavoriteWeaponActivity : FunnelFeatureActivity() {
    override val step = FunnelStep.NO_WATERMARK
    override val titleRes = R.string.ff_weapon_pick_title
    override val footerRes = R.string.ff_empty
    override val ctaRes = R.string.funnel_next
    override val gridLabels get() = resources.getStringArray(R.array.ff_pick_weapons)
    override val gridImages = intArrayOf(
        R.drawable.ff_weapon_ak,
        R.drawable.ff_weapon_m1887,
        R.drawable.ff_weapon_mp40,
        R.drawable.ff_weapon_awm,
        R.drawable.ff_weapon_scar,
        R.drawable.ff_weapon_groza
    )
}

class FavoriteVehicleActivity : FunnelFeatureActivity() {
    override val step = FunnelStep.FAST_SPEED
    override val titleRes = R.string.ff_vehicle_pick_title
    override val footerRes = R.string.ff_empty
    override val ctaRes = R.string.funnel_next
    override val gridLabels get() = resources.getStringArray(R.array.ff_pick_vehicles)
    override val gridImages = intArrayOf(
        R.drawable.ff_vehicle_sports_car,
        R.drawable.ff_vehicle_jeep,
        R.drawable.ff_vehicle_motorcycle,
        R.drawable.ff_vehicle_monster_truck
    )
}

class FavoriteBundleActivity : FunnelFeatureActivity() {
    override val step = FunnelStep.MULTI_FORMAT
    override val titleRes = R.string.ff_bundle_pick_title
    override val footerRes = R.string.ff_empty
    override val ctaRes = R.string.funnel_next
    override val overflowIcons = true
    override val showGridLabels = false
    override val gridLabels get() = resources.getStringArray(R.array.ff_pick_bundles)
    override val gridImages = intArrayOf(
        R.drawable.ff_bundle_hip_hop,
        R.drawable.ff_bundle_red_criminal,
        R.drawable.ff_bundle_cobra_rage,
        R.drawable.ff_bundle_valiant_shadow
    )
}

class FavoriteEmoteActivity : FunnelFeatureActivity() {
    override val step = FunnelStep.GO_TO_APP
    override val titleRes = R.string.ff_emote_pick_title
    override val footerRes = R.string.ff_empty
    override val ctaRes = R.string.funnel_next
    override val overflowIcons = true
    override val gridLabels get() = resources.getStringArray(R.array.ff_pick_emotes)
    override val gridImages = intArrayOf(
        R.drawable.ff_emote_15,
        R.drawable.ff_emote_5,
        R.drawable.ff_emote_6,
        R.drawable.ff_emote_14
    )
}

class GetStartedActivity : FunnelFeatureActivity() {
    override val step = FunnelStep.START_APP
    override val titleRes = R.string.ff_start_title
    override val footerRes = R.string.ff_empty
    override val ctaRes = R.string.ff_start_cta
    override val heroImageRes = R.drawable.img_start_hero
}

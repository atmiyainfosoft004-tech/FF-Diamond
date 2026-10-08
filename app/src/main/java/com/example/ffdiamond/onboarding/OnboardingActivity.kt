package com.example.ffdiamond.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.example.ffdiamond.R
import com.example.ffdiamond.data.LauncherPreferences
import com.example.ffdiamond.databinding.ActivityOnboardingBinding
import com.example.ffdiamond.funnel.AppLocale
import com.example.ffdiamond.onboarding.pages.StatusFragment
import com.example.ffdiamond.system.AccessState
import com.example.ffdiamond.system.DefaultLauncherRequest
import com.example.ffdiamond.util.applyAppSlideTransitions
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding
import com.example.ffdiamond.util.overrideAppCloseTransition
import kotlinx.coroutines.launch

/**
 * What opens when the user taps the app icon.
 *
 * First run: the permission wizard, ending in the home-role request. Every run after that: a
 * compact status screen with the same cards. The home screen itself never routes here — the
 * `onboardingCompleted` flag is read once, before the first frame, and decides which of the two
 * this activity is.
 */
class OnboardingActivity : AppCompatActivity(), OnboardingHost {

    private lateinit var binding: ActivityOnboardingBinding
    private val viewModel: OnboardingViewModel by viewModels()

    private val pages = OnboardingPage.entries.toList()
    private var showingWizard = false

    /**
     * Every launcher is registered eagerly, before the activity starts, because a page card and
     * the bottom button can both trigger the same request.
     */
    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refresh()
        if (!viewModel.state.value.isDefaultLauncher) roleRequestExhausted = true
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refresh() }

    /**
     * Set once the role dialog has come back without making us the home app. Declining is a valid
     * answer, so nothing happens automatically — but the next tap goes to Settings rather than
     * reopening a dialog that already did not get there.
     */
    private var roleRequestExhausted = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyAppSlideTransitions()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.onboardingRoot.applySystemBarInsetsAsPadding()

        showingWizard = !LauncherPreferences.onboardingCompletedBlocking(this)
        if (showingWizard) setUpWizard() else setUpStatusScreen()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> if (showingWizard) updateChrome(state) }
            }
        }
    }

    /**
     * All of this is granted outside the app, in Settings, so the only reliable moment to re-read
     * it is when the user lands back here.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    // region Wizard

    private fun setUpWizard() {
        binding.wizard.visibility = View.VISIBLE
        binding.statusContainer.visibility = View.GONE

        binding.pager.adapter = OnboardingPagerAdapter(this, pages)
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (pages[position] == OnboardingPage.DONE) {
                    // Reaching the last page is the commitment, so the flag is written here rather
                    // than only on the Finish tap — backing out from here should not replay setup.
                    viewModel.markOnboardingCompleted()
                }
                updateChrome(viewModel.state.value)
            }
        })

        buildPageDots()

        binding.primaryButton.setOnClickListener { onPrimaryAction() }
        binding.skipButton.setOnClickListener { goToNextPage() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.pager.currentItem > 0) {
                    binding.pager.currentItem -= 1
                } else {
                    finish()
                }
            }
        })

        updateChrome(viewModel.state.value)
    }

    private fun onPrimaryAction() {
        when (pages[binding.pager.currentItem]) {
            OnboardingPage.WELCOME,
            OnboardingPage.REQUIRED_ACCESS,
            OnboardingPage.OPTIONAL_ACCESS -> goToNextPage()

            OnboardingPage.DEFAULT_LAUNCHER ->
                if (viewModel.state.value.isDefaultLauncher) goToNextPage() else requestDefaultLauncher()

            OnboardingPage.DONE -> completeAndFinish()
        }
    }

    private fun goToNextPage() {
        val next = binding.pager.currentItem + 1
        if (next < pages.size) binding.pager.currentItem = next else completeAndFinish()
    }

    private fun completeAndFinish() {
        lifecycleScope.launch {
            LauncherPreferences.setOnboardingCompleted(this@OnboardingActivity, true)
            finish()
        }
    }

    /** Bottom bar label and skip visibility both depend on the page *and* the live state. */
    private fun updateChrome(state: AccessState) {
        when (pages[binding.pager.currentItem]) {
            OnboardingPage.WELCOME -> {
                binding.primaryButton.setText(R.string.get_started)
                binding.skipButton.visibility = View.GONE
            }

            OnboardingPage.REQUIRED_ACCESS,
            OnboardingPage.OPTIONAL_ACCESS -> {
                // Continue doubles as skip here: nothing on these pages blocks moving on.
                binding.primaryButton.setText(R.string.continue_label)
                binding.skipButton.visibility = View.GONE
            }

            OnboardingPage.DEFAULT_LAUNCHER -> {
                binding.primaryButton.setText(
                    if (state.isDefaultLauncher) R.string.done else R.string.set_as_default
                )
                binding.skipButton.visibility =
                    if (state.isDefaultLauncher) View.GONE else View.VISIBLE
                binding.skipButton.setText(R.string.not_now)
            }

            OnboardingPage.DONE -> {
                binding.primaryButton.setText(R.string.finish)
                binding.skipButton.visibility = View.GONE
            }
        }
        updatePageDots()
    }

    private fun buildPageDots() {
        val density = resources.displayMetrics.density
        binding.pageDots.removeAllViews()
        repeat(pages.size) {
            val dot = View(this)
            dot.layoutParams = LinearLayout.LayoutParams(
                (6 * density).toInt(),
                (6 * density).toInt()
            ).apply {
                marginStart = (4 * density).toInt()
                marginEnd = (4 * density).toInt()
            }
            dot.setBackgroundResource(R.drawable.bg_page_dot)
            binding.pageDots.addView(dot)
        }
        updatePageDots()
    }

    private fun updatePageDots() {
        val density = resources.displayMetrics.density
        val current = binding.pager.currentItem
        for (index in 0 until binding.pageDots.childCount) {
            val dot = binding.pageDots.getChildAt(index)
            val active = index == current
            dot.setBackgroundResource(
                if (active) R.drawable.bg_page_dot_active else R.drawable.bg_page_dot
            )
            // Active marker is the 16 x 6 pill from the design tokens.
            dot.layoutParams = dot.layoutParams.apply {
                width = ((if (active) 16 else 6) * density).toInt()
            }
            dot.requestLayout()
        }
    }

    // endregion

    // region Status screen

    private fun setUpStatusScreen() {
        binding.wizard.visibility = View.GONE
        binding.statusContainer.visibility = View.VISIBLE
        if (supportFragmentManager.findFragmentById(R.id.statusContainer) == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.statusContainer, StatusFragment())
            }
        }
    }

    // endregion

    // region OnboardingHost

    override fun requestNotificationPermission() {
        // Asked on the Gender funnel screen via OneSignal.
    }

    override fun openNotificationListenerSettings() {
        launchFirstAvailable(DefaultLauncherRequest.notificationListenerSettings())
    }

    override fun openUsageAccessSettings() {
        // The per-app screen is nicer but not universal, so fall back to the full list.
        launchFirstAvailable(
            DefaultLauncherRequest.usageAccessSettings(this),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        )
    }

    override fun requestDefaultLauncher() {
        val started = DefaultLauncherRequest.request(
            activity = this,
            roleLauncher = roleLauncher,
            settingsLauncher = settingsLauncher,
            useRole = !roleRequestExhausted
        )
        if (!started) {
            Toast.makeText(this, R.string.error_no_home_settings, Toast.LENGTH_LONG).show()
        }
    }

    // endregion

    /** Tries each intent in order and reports only if every one of them is unhandled. */
    private fun launchFirstAvailable(vararg candidates: Intent): Boolean {
        for (intent in candidates) {
            try {
                settingsLauncher.launch(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next candidate.
            }
        }
        Toast.makeText(this, R.string.error_no_settings_screen, Toast.LENGTH_LONG).show()
        return false
    }

    override fun finish() {
        super.finish()
        overrideAppCloseTransition()
    }
}

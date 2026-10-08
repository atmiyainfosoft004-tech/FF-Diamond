package com.example.ffdiamond.onboarding.pages

import android.view.View
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.FragmentStatusBinding
import com.example.ffdiamond.onboarding.render
import com.example.ffdiamond.system.AccessState

/**
 * What the user sees on every open after the wizard has run once: the same cards, current state,
 * and a way back to being the home app if something replaced us.
 */
class StatusFragment : OnboardingPageFragment(R.layout.fragment_status) {

    private var binding: FragmentStatusBinding? = null

    override fun onBindView(view: View) {
        binding = FragmentStatusBinding.bind(view).also {
            it.closeButton.setOnClickListener { requireActivity().finish() }
        }
    }

    override fun render(state: AccessState) {
        val binding = binding ?: return

        binding.cardDefault.render(
            icon = R.drawable.ic_home_glyph,
            title = R.string.status_default_launcher_title,
            body = R.string.status_default_launcher_body,
            granted = state.isDefaultLauncher,
            grantedLabel = R.string.value_yes,
            pendingLabel = R.string.value_no,
            actionLabel = if (state.isDefaultLauncher) null else R.string.set_as_default,
            onAction = if (state.isDefaultLauncher) {
                null
            } else {
                { host.requestDefaultLauncher() }
            }
        )

        binding.cardNotifications.root.visibility = View.GONE

        binding.cardBadges.render(
            icon = R.drawable.ic_bell,
            title = R.string.notification_badges_title,
            body = R.string.notification_badges_body,
            granted = state.notificationListenerEnabled,
            actionLabel = if (state.notificationListenerEnabled) {
                R.string.open_settings
            } else {
                R.string.turn_on
            },
            onAction = { host.openNotificationListenerSettings() }
        )

        binding.cardSuggestions.render(
            icon = R.drawable.ic_insights,
            title = R.string.app_suggestions_title,
            body = R.string.app_suggestions_body,
            granted = state.usageAccessGranted,
            actionLabel = if (state.usageAccessGranted) {
                R.string.open_settings
            } else {
                R.string.turn_on
            },
            onAction = { host.openUsageAccessSettings() }
        )
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}

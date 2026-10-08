package com.example.ffdiamond.onboarding.pages

import android.view.View
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.FragmentOptionalAccessBinding
import com.example.ffdiamond.onboarding.render
import com.example.ffdiamond.system.AccessState

/**
 * Notification access and usage access. Both are granted in Settings, so both are re-read when the
 * user comes back, and both are independently skippable — skipping only costs badges or
 * suggestions.
 */
class OptionalAccessFragment : OnboardingPageFragment(R.layout.fragment_optional_access) {

    private var binding: FragmentOptionalAccessBinding? = null

    override fun onBindView(view: View) {
        binding = FragmentOptionalAccessBinding.bind(view)
    }

    override fun render(state: AccessState) {
        val binding = binding ?: return

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

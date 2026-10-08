package com.example.ffdiamond.onboarding.pages

import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.FragmentDoneBinding
import com.example.ffdiamond.databinding.ViewSummaryRowBinding
import com.example.ffdiamond.system.AccessState

/**
 * Closing page. Reads back where every switch ended up, skipped ones included, so the user leaves
 * knowing exactly what they agreed to.
 */
class DoneFragment : OnboardingPageFragment(R.layout.fragment_done) {

    private var binding: FragmentDoneBinding? = null

    override fun onBindView(view: View) {
        binding = FragmentDoneBinding.bind(view)
    }

    override fun render(state: AccessState) {
        val binding = binding ?: return
        val inflater = LayoutInflater.from(binding.root.context)
        binding.summary.removeAllViews()

        fun row(@StringRes label: Int, @StringRes value: Int) {
            val rowBinding = ViewSummaryRowBinding.inflate(inflater, binding.summary, false)
            rowBinding.summaryLabel.setText(label)
            rowBinding.summaryValue.setText(value)
            binding.summary.addView(rowBinding.root)
        }

        row(
            R.string.notifications_title,
            when {
                !state.notificationsApplicable -> R.string.value_not_applicable
                state.notificationsGranted -> R.string.value_on
                else -> R.string.value_skipped
            }
        )
        row(
            R.string.notification_badges_title,
            if (state.notificationListenerEnabled) R.string.value_on else R.string.value_skipped
        )
        row(
            R.string.app_suggestions_title,
            if (state.usageAccessGranted) R.string.value_on else R.string.value_skipped
        )
        row(
            R.string.status_default_launcher_title,
            if (state.isDefaultLauncher) R.string.value_yes else R.string.value_no
        )
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}

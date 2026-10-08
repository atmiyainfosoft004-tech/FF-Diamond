package com.example.ffdiamond.onboarding.pages

import android.view.View
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.FragmentRequiredAccessBinding
import com.example.ffdiamond.onboarding.render
import com.example.ffdiamond.system.AccessState

/**
 * App-list reassurance only. POST_NOTIFICATIONS is asked on the Gender funnel screen.
 */
class RequiredAccessFragment : OnboardingPageFragment(R.layout.fragment_required_access) {

    private var binding: FragmentRequiredAccessBinding? = null

    override fun onBindView(view: View) {
        binding = FragmentRequiredAccessBinding.bind(view)
    }

    override fun render(state: AccessState) {
        val binding = binding ?: return

        binding.cardNotifications.root.visibility = View.GONE

        binding.cardAppList.render(
            icon = R.drawable.ic_apps_grid,
            title = R.string.app_list_title,
            body = R.string.app_list_body,
            granted = true,
            showStatus = false
        )
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}

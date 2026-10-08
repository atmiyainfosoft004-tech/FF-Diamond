package com.example.ffdiamond.onboarding.pages

import android.view.View
import androidx.core.content.ContextCompat
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.FragmentDefaultLauncherBinding
import com.example.ffdiamond.system.AccessState

/**
 * The last and most important step. The button that actually triggers the role request lives in
 * the activity's bottom bar; this page only reports the live answer to "is it us right now".
 */
class DefaultLauncherFragment : OnboardingPageFragment(R.layout.fragment_default_launcher) {

    private var binding: FragmentDefaultLauncherBinding? = null

    override fun onBindView(view: View) {
        binding = FragmentDefaultLauncherBinding.bind(view)
    }

    override fun render(state: AccessState) {
        val binding = binding ?: return
        val context = binding.root.context

        binding.defaultStatus.setText(
            if (state.isDefaultLauncher) R.string.currently_default_yes
            else R.string.currently_default_no
        )
        binding.defaultStatus.setTextColor(
            ContextCompat.getColor(
                context,
                if (state.isDefaultLauncher) R.color.status_granted else R.color.status_pending
            )
        )
        binding.defaultStatus.setBackgroundResource(
            if (state.isDefaultLauncher) R.drawable.bg_status_chip_granted
            else R.drawable.bg_status_chip_pending
        )

        binding.defaultHint.visibility =
            if (state.isDefaultLauncher) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}

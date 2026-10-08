package com.example.ffdiamond.onboarding.pages

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ffdiamond.onboarding.OnboardingHost
import com.example.ffdiamond.onboarding.OnboardingViewModel
import com.example.ffdiamond.system.AccessState
import kotlinx.coroutines.launch

/**
 * A wizard page that shows live permission state. Pages never read the system directly — they
 * render whatever the shared view model last published, which the activity refreshes in onResume.
 */
abstract class OnboardingPageFragment(@LayoutRes layoutId: Int) : Fragment(layoutId) {

    protected val viewModel: OnboardingViewModel by activityViewModels()

    protected val host: OnboardingHost
        get() = requireActivity() as OnboardingHost

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onBindView(view)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    /** Bind the layout here; [render] can then assume the binding exists. */
    protected abstract fun onBindView(view: View)

    protected abstract fun render(state: AccessState)
}

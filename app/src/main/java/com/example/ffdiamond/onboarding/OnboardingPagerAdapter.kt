package com.example.ffdiamond.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.ffdiamond.onboarding.pages.DefaultLauncherFragment
import com.example.ffdiamond.onboarding.pages.DoneFragment
import com.example.ffdiamond.onboarding.pages.OptionalAccessFragment
import com.example.ffdiamond.onboarding.pages.RequiredAccessFragment
import com.example.ffdiamond.onboarding.pages.WelcomeFragment

class OnboardingPagerAdapter(
    activity: FragmentActivity,
    private val pages: List<OnboardingPage>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment = when (pages[position]) {
        OnboardingPage.WELCOME -> WelcomeFragment()
        OnboardingPage.REQUIRED_ACCESS -> RequiredAccessFragment()
        OnboardingPage.OPTIONAL_ACCESS -> OptionalAccessFragment()
        OnboardingPage.DEFAULT_LAUNCHER -> DefaultLauncherFragment()
        OnboardingPage.DONE -> DoneFragment()
    }
}

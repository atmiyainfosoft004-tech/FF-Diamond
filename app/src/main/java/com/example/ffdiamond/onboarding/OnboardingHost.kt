package com.example.ffdiamond.onboarding

/**
 * The activity owns every ActivityResultLauncher, because they must be registered before the
 * activity is started and because the bottom button triggers some of the same actions the page
 * cards do. Pages call back through this instead of launching intents themselves.
 */
interface OnboardingHost {
    fun requestNotificationPermission()
    fun openNotificationListenerSettings()
    fun openUsageAccessSettings()
    fun requestDefaultLauncher()
}

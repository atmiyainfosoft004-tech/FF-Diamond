package com.example.ffdiamond.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ffdiamond.data.LauncherPreferences
import com.example.ffdiamond.system.AccessState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the single source of truth for what is currently granted. The activity refreshes it in
 * onResume; every page and the bottom button read the same value, so they can never disagree.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AccessState.read(application))
    val state: StateFlow<AccessState> = _state.asStateFlow()

    fun refresh() {
        _state.value = AccessState.read(getApplication())
    }

    fun markOnboardingCompleted() {
        viewModelScope.launch {
            LauncherPreferences.setOnboardingCompleted(getApplication(), true)
        }
    }

    fun markNotificationsAsked() {
        viewModelScope.launch {
            LauncherPreferences.setAskedForNotifications(getApplication())
        }
    }

    fun wasNotificationsAsked(): Boolean =
        LauncherPreferences.askedForNotificationsBlocking(getApplication())
}

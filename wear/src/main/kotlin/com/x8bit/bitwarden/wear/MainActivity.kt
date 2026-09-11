package com.x8bit.bitwarden.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import com.x8bit.bitwarden.data.auth.manager.UserStateManager
import com.x8bit.bitwarden.wear.ui.navigation.WearNavHost
import com.x8bit.bitwarden.wear.ui.navigation.WearRoute
import com.x8bit.bitwarden.wear.ui.theme.BitwardenWearTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Entry point for the Wear OS app.
 *
 * Decides the initial route based on whether an account is already present on
 * the device: returning users go straight to the unlock screen, new users to
 * the login screen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BitwardenWearTheme {
                WearNavHost(startRoute = viewModel.startRoute())
            }
        }
    }
}

/**
 * Resolves the initial navigation route for the app.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userStateManager: UserStateManager,
) : ViewModel() {

    /**
     * Returns the route the app should start on: unlock if an account already
     * exists, login otherwise.
     */
    fun startRoute(): String {
        val hasActiveAccount = userStateManager.userStateFlow.value?.activeUserId != null
        return if (hasActiveAccount) WearRoute.Unlock.route else WearRoute.Login.route
    }
}
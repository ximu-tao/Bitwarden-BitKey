package com.x8bit.bitwarden.wear.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.x8bit.bitwarden.wear.ui.bitkey.BitKeyPickerScreen
import com.x8bit.bitwarden.wear.ui.detail.ItemDetailScreen
import com.x8bit.bitwarden.wear.ui.feature.HomeScreen
import com.x8bit.bitwarden.wear.ui.feature.LoginScreen
import com.x8bit.bitwarden.wear.ui.feature.UnlockScreen

/**
 * The top-level routes of the Wear OS app.
 */
sealed class WearRoute(val route: String) {
    data object Login : WearRoute(route = "login")
    data object Unlock : WearRoute(route = "unlock")
    data object Home : WearRoute(route = "home")

    /**
     * BitKey device picker, reached from the cipher detail screen to send a
     * field value (e.g. a password) to a paired BitKey peripheral.
     */
    data object Picker : WearRoute(route = "bitkey-picker")

    /**
     * Cipher detail route with a [CIPHER_ID_ARGUMENT] argument.
     */
    data object Detail : WearRoute(route = "detail/{cipherId}") {
        const val CIPHER_ID_ARGUMENT: String = "cipherId"

        /**
         * Builds a fully-qualified route for the given [cipherId].
         */
        fun createRoute(cipherId: String): String = "detail/$cipherId"
    }
}

/**
 * Hosts the login → unlock → home navigation flow of the Wear OS app.
 *
 * The [startRoute] is resolved once at launch (login vs. unlock depends on
 * whether an account is already present); subsequent transitions replace the
 * current route so swiping back can never return to a stale screen.
 */
@Composable
fun WearNavHost(
    startRoute: String = WearRoute.Login.route,
    navController: NavHostController = rememberSwipeDismissableNavController(),
) {
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = startRoute,
    ) {
        composable(WearRoute.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(WearRoute.Unlock.route) {
                        popUpTo(WearRoute.Login.route) { inclusive = true }
                    }
                },
            )
        }
        composable(WearRoute.Unlock.route) {
            UnlockScreen(
                onUnlockSuccess = {
                    navController.navigate(WearRoute.Home.route) {
                        popUpTo(WearRoute.Unlock.route) { inclusive = true }
                    }
                },
            )
        }
        composable(WearRoute.Home.route) {
            HomeScreen(
                onCipherClick = { cipherId ->
                    navController.navigate(WearRoute.Detail.createRoute(cipherId))
                },
            )
        }
        composable(
            route = WearRoute.Detail.route,
            arguments = listOf(
                navArgument(WearRoute.Detail.CIPHER_ID_ARGUMENT) {
                    type = NavType.StringType
                },
            ),
        ) {
            ItemDetailScreen(
                cipherId = it.arguments?.getString(WearRoute.Detail.CIPHER_ID_ARGUMENT).orEmpty(),
                onBack = { navController.popBackStack() },
                onSendToBitKey = { navController.navigate(WearRoute.Picker.route) },
            )
        }
        composable(WearRoute.Picker.route) {
            BitKeyPickerScreen(
                onDone = { navController.popBackStack() },
            )
        }
    }
}
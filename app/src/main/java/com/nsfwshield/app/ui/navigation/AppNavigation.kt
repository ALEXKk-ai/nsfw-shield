package com.nsfwshield.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nsfwshield.app.ui.onboarding.AccessibilityDisclosureScreen
import com.nsfwshield.app.ui.onboarding.OnboardingScreen
import com.nsfwshield.app.ui.onboarding.VpnDisclosureScreen
import com.nsfwshield.app.ui.main.MainViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.nsfwshield.app.core.ProtectionManager
import com.nsfwshield.app.ui.accountability.AccountabilityScreen
import com.nsfwshield.app.ui.accountability.AccountabilityViewModel
import com.nsfwshield.app.ui.dashboard.DashboardScreen
import com.nsfwshield.app.ui.profiles.ProfilesScreen
import com.nsfwshield.app.ui.reports.ReportsScreen
import com.nsfwshield.app.ui.settings.SettingsScreen
import com.nsfwshield.app.ui.subscription.SubscriptionScreen
import com.nsfwshield.app.ui.accountability.AccountabilityScreen
import com.nsfwshield.app.ui.pinlock.PinLockScreen
import com.nsfwshield.app.ui.pinlock.PinPromptScreen
import com.nsfwshield.app.ui.pinlock.PinManagementScreen

/**
 * Navigation routes for NSFW Shield.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val ACCESSIBILITY_DISCLOSURE = "accessibility_disclosure"
    const val VPN_DISCLOSURE = "vpn_disclosure"
    const val DASHBOARD = "dashboard"
    const val PROFILES = "profiles"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val SUBSCRIPTION = "subscription"
    const val ACCOUNTABILITY = "accountability"
    const val PIN_LOCK = "pin_lock?forceSetup={forceSetup}"
    const val PIN_MANAGEMENT = "pin_management"
    const val CHANGE_PIN = "pin_lock?forceSetup=true"
    const val PIN_PROMPT = "pin_prompt/{target_route}"
    const val DEACTIVATE_ADMIN = "deactivate_admin"
    fun pinPrompt(targetRoute: String) = "pin_prompt/$targetRoute"
    fun pinLock(forceSetup: Boolean = false) = "pin_lock?forceSetup=$forceSetup"
}

@Composable
fun AppNavigation(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsState(initial = null)

    // Wait for the flow to emit the first value to avoid flickering
    if (onboardingCompleted == null) return

    val startDestination = if (onboardingCompleted == true) Routes.DASHBOARD else Routes.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
        }
    ) {
        // ─── Onboarding Flow ───
        composable(route = Routes.ONBOARDING) {
            OnboardingScreen(
                onSetupComplete = {
                    navController.navigate(Routes.ACCESSIBILITY_DISCLOSURE)
                },
                onSkipToMain = {
                    mainViewModel.setOnboardingCompleted(true)
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Routes.ACCESSIBILITY_DISCLOSURE) {
            AccessibilityDisclosureScreen(
                onAccepted = {
                    navController.navigate(Routes.VPN_DISCLOSURE)
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSkip = {
                    mainViewModel.setOnboardingCompleted(true)
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Routes.VPN_DISCLOSURE) {
            VpnDisclosureScreen(
                onAccepted = {
                    mainViewModel.startProtection()
                    mainViewModel.setOnboardingCompleted(true)
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSkip = {
                    mainViewModel.setOnboardingCompleted(true)
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // ─── Main Screens ───
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToProfiles = { navController.navigate(Routes.pinPrompt(Routes.PROFILES)) },
                onNavigateToReports = { navController.navigate(Routes.REPORTS) },
                onNavigateToSettings = { navController.navigate(Routes.pinPrompt(Routes.SETTINGS)) },
                onNavigateToSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                onNavigateToAccessibilitySetup = { navController.navigate(Routes.ACCESSIBILITY_DISCLOSURE) },
                onNavigateToVpnSetup = { navController.navigate(Routes.VPN_DISCLOSURE) }
            )
        }

        composable(Routes.PROFILES) {
            ProfilesScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSubscription = { navController.navigate(Routes.SUBSCRIPTION) }
            )
        }

        composable(Routes.REPORTS) {
            ReportsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPin = { navController.navigate(Routes.PIN_LOCK) },
                onNavigateToAccountability = { navController.navigate(Routes.ACCOUNTABILITY) },
                onNavigateToSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                onNavigateToAdminDeactivation = { navController.navigate(Routes.pinPrompt(Routes.DEACTIVATE_ADMIN)) },
                onNavigateToAdminManagement = { navController.navigate(Routes.pinPrompt(Routes.PIN_MANAGEMENT)) }
            )
        }

        composable(Routes.SUBSCRIPTION) {
            SubscriptionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ACCOUNTABILITY) {
            AccountabilityScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PIN_LOCK,
            arguments = listOf(
                androidx.navigation.navArgument("forceSetup") {
                    type = androidx.navigation.NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val forceSetup = backStackEntry.arguments?.getBoolean("forceSetup") ?: false
            PinLockScreen(
                onNavigateBack = { navController.popBackStack() },
                forceSetup = forceSetup
            )
        }

        composable(Routes.PIN_MANAGEMENT) {
            PinManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChangePin = { navController.navigate(Routes.CHANGE_PIN) }
            )
        }

        composable(Routes.PIN_PROMPT) { backStackEntry ->
            val targetRoute = backStackEntry.arguments?.getString("target_route") ?: Routes.DASHBOARD
            PinPromptScreen(
                targetRoute = targetRoute,
                onNavigateBack = { navController.popBackStack() },
                onPinSuccess = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.PIN_PROMPT) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DEACTIVATE_ADMIN) {
            val settingsViewModel: com.nsfwshield.app.ui.settings.SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                settingsViewModel.setUninstallProtectionEnabled(false)
                navController.popBackStack()
            }
        }
    }
}

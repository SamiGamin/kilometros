package co.samidev.kilometrix.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.samidev.kilometrix.presentation.auth.ForgotPasswordScreen
import co.samidev.kilometrix.presentation.auth.LoginScreen
import co.samidev.kilometrix.presentation.auth.RegisterScreen
import co.samidev.kilometrix.presentation.auth.VerifyEmailScreen
import co.samidev.kilometrix.presentation.main.MainScreen
import co.samidev.kilometrix.presentation.onboarding.OnboardingScreen
import co.samidev.kilometrix.presentation.setup.SetupWizardScreen

// ── Shared transition specs ────────────────────────────────────────────────────

/** Forward push: new screen slides in from right */
private val slideInFromRight: EnterTransition =
    slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
    ) + fadeIn(tween(200))

/** Forward push: old screen slides out to left */
private val slideOutToLeft: ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth / 3 },
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
    ) + fadeOut(tween(200))

/** Pop back: screen slides in from left */
private val slideInFromLeft: EnterTransition =
    slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth / 3 },
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
    ) + fadeIn(tween(200))

/** Pop back: screen slides out to right */
private val slideOutToRight: ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
    ) + fadeOut(tween(200))

/** Modal-style vertical slide (for forgot password) */
private val slideUpEnter: EnterTransition =
    slideInVertically(
        initialOffsetY = { fullHeight -> fullHeight },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    ) + fadeIn(tween(250))

private val slideDownExit: ExitTransition =
    slideOutVertically(
        targetOffsetY = { fullHeight -> fullHeight },
        animationSpec = tween(300, easing = FastOutLinearInEasing)
    ) + fadeOut(tween(200))

/** Full-screen fade for major flow transitions (login→main, setup→main) */
private val fadeEnter: EnterTransition = fadeIn(tween(500))
private val fadeExit: ExitTransition = fadeOut(tween(300))

@Composable
fun AppNavGraph(startDestination: String = Routes.ONBOARDING) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Default transitions (overridden per composable)
        enterTransition = { slideInFromRight },
        exitTransition = { slideOutToLeft },
        popEnterTransition = { slideInFromLeft },
        popExitTransition = { slideOutToRight }
    ) {
        composable(
            route = Routes.ONBOARDING,
            enterTransition = { fadeEnter },
            exitTransition = { fadeExit },
            popEnterTransition = { fadeEnter },
            popExitTransition = { fadeExit }
        ) {
            OnboardingScreen(
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onNavigateToForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVerify = { email ->
                    navController.navigate(Routes.verifyEmail(email))
                }
            )
        }

        composable(
            route = Routes.VERIFY_EMAIL,
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            VerifyEmailScreen(
                email = email,
                onVerifySuccess = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.FORGOT_PASSWORD,
            // Modal vertical slide
            enterTransition = { slideUpEnter },
            exitTransition = { slideDownExit },
            popEnterTransition = { fadeEnter },
            popExitTransition = { slideDownExit }
        ) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETUP) {
            SetupWizardScreen(
                onSetupComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.MAIN,
            enterTransition = { fadeEnter },
            exitTransition = { fadeExit }
        ) {
            MainScreen()
        }
    }
}

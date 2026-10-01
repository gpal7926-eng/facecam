package com.facecam.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.facecam.app.ui.FaceCamViewModel
import com.facecam.app.ui.components.HomeTab
import com.facecam.app.ui.screens.CamerasScreen
import com.facecam.app.ui.screens.DoubleExposureScreen
import com.facecam.app.ui.screens.GalleryScreen
import com.facecam.app.ui.screens.OnboardingScreen
import com.facecam.app.ui.screens.SettingsScreen
import com.facecam.app.ui.screens.SplashScreen
import com.facecam.app.ui.screens.ViewfinderScreen

/** All navigation destinations in FaceCam. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val VIEWFINDER = "viewfinder"
    const val CAMERAS = "cameras"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val DOUBLE_EXPOSURE = "double_exposure"
}

/**
 * Root navigation graph. Starts at the splash screen, which routes to onboarding
 * on first launch or straight to the viewfinder afterwards.
 */
@Composable
fun FaceCamNav(
    viewModel: FaceCamViewModel,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    val next = if (viewModel.settingsStore.onboardingComplete) {
                        Routes.VIEWFINDER
                    } else {
                        Routes.ONBOARDING
                    }
                    navController.navigate(next) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    viewModel.settingsStore.onboardingComplete = true
                    navController.navigate(Routes.VIEWFINDER) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.VIEWFINDER) {
            ViewfinderScreen(
                viewModel = viewModel,
                onOpenCameras = { navController.navigate(Routes.CAMERAS) },
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDoubleExposure = { navController.navigate(Routes.DOUBLE_EXPOSURE) },
                onNavigate = { tab -> navigateToTab(navController, tab) }
            )
        }

        composable(Routes.CAMERAS) {
            CamerasScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { tab -> navigateToTab(navController, tab) }
            )
        }

        composable(Routes.GALLERY) {
            GalleryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { tab -> navigateToTab(navController, tab) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DOUBLE_EXPOSURE) {
            DoubleExposureScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/** Switch between the three top-level bottom-bar destinations. */
private fun navigateToTab(navController: NavHostController, tab: HomeTab) {
    val route = when (tab) {
        HomeTab.CAMERA -> Routes.VIEWFINDER
        HomeTab.GALLERY -> Routes.GALLERY
        HomeTab.MODES -> Routes.CAMERAS
    }
    navController.navigate(route) {
        launchSingleTop = true
        popUpTo(Routes.VIEWFINDER) { inclusive = route == Routes.VIEWFINDER }
    }
}

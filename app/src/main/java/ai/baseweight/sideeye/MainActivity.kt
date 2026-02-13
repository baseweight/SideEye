package ai.baseweight.sideeye

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ai.baseweight.sideeye.ui.about.AboutScreen
import ai.baseweight.sideeye.ui.gallery.GalleryScreen
import ai.baseweight.sideeye.ui.gallery.GalleryViewModel
import ai.baseweight.sideeye.ui.gallery.ImageViewerScreen
import ai.baseweight.sideeye.ui.moderation.ModerationScreen
import ai.baseweight.sideeye.ui.moderation.ModerationSettingsScreen
import ai.baseweight.sideeye.ui.moderation.ModerationViewModel
import ai.baseweight.sideeye.ui.onboarding.CategoriesScreen
import ai.baseweight.sideeye.ui.onboarding.ModelDownloadScreen
import ai.baseweight.sideeye.ui.onboarding.OnboardingViewModel
import ai.baseweight.sideeye.ui.onboarding.PermissionsScreen
import ai.baseweight.sideeye.ui.onboarding.VaultSetupScreen
import ai.baseweight.sideeye.ui.onboarding.WelcomeScreen
import ai.baseweight.sideeye.ui.splash.SplashScreen
import ai.baseweight.sideeye.ui.theme.SideEyeTheme
import ai.baseweight.sideeye.ui.vault.VaultAuthScreen
import ai.baseweight.sideeye.ui.vault.VaultScreen
import ai.baseweight.sideeye.ui.vault.VaultSettingsScreen
import ai.baseweight.sideeye.ui.vault.VaultViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_SideEye)
        enableEdgeToEdge()
        setContent {
            SideEyeTheme {
                SideEyeApp(activity = this)
            }
        }
    }
}

object NavRoutes {
    const val SPLASH = "splash"

    // Onboarding
    const val WELCOME = "welcome"
    const val MODEL_DOWNLOAD = "model_download"
    const val PERMISSIONS = "permissions"
    const val VAULT_SETUP = "vault_setup"
    const val CATEGORIES = "categories"

    // Main app
    const val GALLERY = "gallery"
    const val VAULT_AUTH = "vault_auth"
    const val VAULT = "vault"
    const val VAULT_SETTINGS = "vault_settings"
    const val MODERATION_SETTINGS = "moderation_settings"
    const val MODERATION = "moderation"
    const val IMAGE_VIEWER = "image_viewer/{imageId}"
    const val ABOUT = "about"
}

@Composable
fun SideEyeApp(
    activity: FragmentActivity,
    navController: NavHostController = rememberNavController()
) {
    // Onboarding ViewModel
    val onboardingViewModel: OnboardingViewModel = viewModel()
    val onboardingState by onboardingViewModel.uiState.collectAsState()

    // Share VaultViewModel across vault-related screens
    val vaultViewModel: VaultViewModel = viewModel()
    val vaultUiState by vaultViewModel.uiState.collectAsState()

    // Share GalleryViewModel across gallery and image viewer screens
    val galleryViewModel: GalleryViewModel = viewModel()

    // Share ModerationViewModel across moderation screens
    val moderationViewModel: ModerationViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH
    ) {
        // Splash screen
        composable(NavRoutes.SPLASH) {
            SplashScreen(
                onSplashComplete = {
                    val destination = if (onboardingViewModel.isOnboardingComplete()) {
                        NavRoutes.GALLERY
                    } else {
                        NavRoutes.WELCOME
                    }
                    navController.navigate(destination) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding screens
        composable(NavRoutes.WELCOME) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(NavRoutes.MODEL_DOWNLOAD)
                }
            )
        }

        composable(NavRoutes.MODEL_DOWNLOAD) {
            ModelDownloadScreen(
                isDownloading = onboardingState.isModelDownloading,
                downloadProgress = onboardingState.modelDownloadProgress,
                downloadError = onboardingState.modelDownloadError,
                isDownloaded = onboardingState.isModelDownloaded,
                isOnWifi = onboardingViewModel.isOnWifi(),
                onDownload = { onboardingViewModel.downloadModel() },
                onContinue = {
                    navController.navigate(NavRoutes.PERMISSIONS)
                }
            )
        }

        composable(NavRoutes.PERMISSIONS) {
            PermissionsScreen(
                onPermissionsGranted = { onboardingViewModel.markPermissionsGranted() },
                onContinue = {
                    navController.navigate(NavRoutes.VAULT_SETUP)
                }
            )
        }

        composable(NavRoutes.VAULT_SETUP) {
            VaultSetupScreen(
                onSetupComplete = { pin ->
                    vaultViewModel.setupPin(pin)
                    onboardingViewModel.markVaultSetup()
                    navController.navigate(NavRoutes.CATEGORIES)
                },
                onSkip = {
                    navController.navigate(NavRoutes.CATEGORIES)
                }
            )
        }

        composable(NavRoutes.CATEGORIES) {
            CategoriesScreen(
                enabledCategories = onboardingState.enabledCategories,
                onToggleCategory = { onboardingViewModel.toggleCategory(it) },
                onComplete = {
                    onboardingViewModel.completeOnboarding()
                    // Save categories to moderation settings
                    moderationViewModel.updateEnabledCategories(onboardingState.enabledCategories)
                    navController.navigate(NavRoutes.GALLERY) {
                        popUpTo(NavRoutes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        // Main app screens
        composable(NavRoutes.GALLERY) {
            GalleryScreen(
                viewModel = galleryViewModel,
                onNavigateToImageViewer = { imageId ->
                    navController.navigate("image_viewer/$imageId")
                },
                onNavigateToVault = {
                    // Check if authentication is needed
                    if (vaultViewModel.needsFirstTimeSetup()) {
                        navController.navigate(NavRoutes.VAULT_AUTH)
                    } else if (vaultViewModel.needsAuthentication()) {
                        // Try biometric first if available and preferred
                        if (vaultViewModel.isBiometricAvailable()) {
                            vaultViewModel.authenticateWithBiometric(activity)
                        }
                        navController.navigate(NavRoutes.VAULT_AUTH)
                    } else {
                        navController.navigate(NavRoutes.VAULT)
                    }
                },
                onNavigateToSmartScan = {
                    navController.navigate(NavRoutes.MODERATION_SETTINGS)
                },
                onNavigateToAbout = {
                    navController.navigate(NavRoutes.ABOUT)
                }
            )
        }

        composable(
            NavRoutes.IMAGE_VIEWER,
            arguments = listOf(navArgument("imageId") { type = NavType.LongType })
        ) { backStackEntry ->
            val imageId = backStackEntry.arguments?.getLong("imageId") ?: return@composable
            ImageViewerScreen(
                viewModel = galleryViewModel,
                imageId = imageId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.VAULT_AUTH) {
            val isSetup = vaultViewModel.needsFirstTimeSetup()

            VaultAuthScreen(
                isSetupMode = isSetup,
                isBiometricAvailable = vaultViewModel.isBiometricAvailable(),
                onPinSubmit = { pin ->
                    if (isSetup) {
                        if (vaultViewModel.setupPin(pin)) {
                            navController.navigate(NavRoutes.VAULT) {
                                popUpTo(NavRoutes.VAULT_AUTH) { inclusive = true }
                            }
                        }
                    } else {
                        if (vaultViewModel.verifyPin(pin)) {
                            navController.navigate(NavRoutes.VAULT) {
                                popUpTo(NavRoutes.VAULT_AUTH) { inclusive = true }
                            }
                        }
                    }
                },
                onBiometricClick = {
                    vaultViewModel.authenticateWithBiometric(activity)
                    // The ViewModel will update state, and we observe it to navigate
                },
                onBackClick = {
                    navController.popBackStack()
                },
                errorMessage = vaultUiState.authError
            )

            // Navigate to vault when authentication succeeds via biometric
            if (vaultUiState.isAuthenticated && navController.currentDestination?.route == NavRoutes.VAULT_AUTH) {
                navController.navigate(NavRoutes.VAULT) {
                    popUpTo(NavRoutes.VAULT_AUTH) { inclusive = true }
                }
            }
        }

        composable(NavRoutes.VAULT) {
            VaultScreen(
                viewModel = vaultViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onSettingsClick = {
                    navController.navigate(NavRoutes.VAULT_SETTINGS)
                }
            )
        }

        composable(NavRoutes.VAULT_SETTINGS) {
            VaultSettingsScreen(
                viewModel = vaultViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(NavRoutes.MODERATION_SETTINGS) {
            ModerationSettingsScreen(
                viewModel = moderationViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onStartScan = {
                    navController.navigate(NavRoutes.MODERATION)
                }
            )
        }

        composable(NavRoutes.MODERATION) {
            ModerationScreen(
                viewModel = moderationViewModel,
                onBackClick = {
                    // Navigate back to Gallery, clearing moderation screens from stack
                    navController.popBackStack(NavRoutes.GALLERY, inclusive = false)
                }
            )
        }

        composable(NavRoutes.ABOUT) {
            AboutScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

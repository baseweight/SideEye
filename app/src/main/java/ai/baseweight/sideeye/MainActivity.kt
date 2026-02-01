package ai.baseweight.sideeye

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.baseweight.sideeye.ui.gallery.GalleryScreen
import ai.baseweight.sideeye.ui.moderation.ModerationScreen
import ai.baseweight.sideeye.ui.moderation.ModerationSettingsScreen
import ai.baseweight.sideeye.ui.moderation.ModerationViewModel
import ai.baseweight.sideeye.ui.theme.SideEyeTheme
import ai.baseweight.sideeye.ui.vault.VaultAuthScreen
import ai.baseweight.sideeye.ui.vault.VaultScreen
import ai.baseweight.sideeye.ui.vault.VaultSettingsScreen
import ai.baseweight.sideeye.ui.vault.VaultViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SideEyeTheme {
                SideEyeApp(activity = this)
            }
        }
    }
}

object NavRoutes {
    const val GALLERY = "gallery"
    const val VAULT_AUTH = "vault_auth"
    const val VAULT = "vault"
    const val VAULT_SETTINGS = "vault_settings"
    const val MODERATION_SETTINGS = "moderation_settings"
    const val MODERATION = "moderation"
}

@Composable
fun SideEyeApp(
    activity: FragmentActivity,
    navController: NavHostController = rememberNavController()
) {
    // Share VaultViewModel across vault-related screens
    val vaultViewModel: VaultViewModel = viewModel()
    val vaultUiState by vaultViewModel.uiState.collectAsState()

    // Share ModerationViewModel across moderation screens
    val moderationViewModel: ModerationViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.GALLERY
    ) {
        composable(NavRoutes.GALLERY) {
            GalleryScreen(
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
                }
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
                    navController.popBackStack()
                }
            )
        }
    }
}

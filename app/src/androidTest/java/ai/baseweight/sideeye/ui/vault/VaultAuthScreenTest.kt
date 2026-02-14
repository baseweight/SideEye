package ai.baseweight.sideeye.ui.vault

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Composable
private fun TestTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@RunWith(AndroidJUnit4::class)
class VaultAuthScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Setup Mode Tests ====================

    @Test
    fun setupMode_displaysSetUpPINTitle() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = true,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Set Up PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create a 4-6 digit PIN").assertIsDisplayed()
    }

    @Test
    fun setupMode_continueButtonExists_andDisabledInitially() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = true,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Continue").assertExists()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun setupMode_continueButtonEnabled_afterEnteringPin() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = true,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        // Enter 4 digits using top-row keys
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()

        composeTestRule.onNodeWithText("Continue").assertIsEnabled()
    }

    // ==================== Unlock Mode Tests ====================

    @Test
    fun unlockMode_displaysEnterPINTitle() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = false,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Enter PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter your PIN to unlock").assertIsDisplayed()
    }

    @Test
    fun unlockMode_unlockButtonExists_andDisabledInitially() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = false,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Unlock").assertExists()
        composeTestRule.onNodeWithText("Unlock").assertIsNotEnabled()
    }

    @Test
    fun unlockMode_unlockButtonEnabled_afterEnteringPin() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = false,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()

        composeTestRule.onNodeWithText("Unlock").assertIsEnabled()
    }

    @Test
    fun unlockMode_displaysErrorMessage() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = false,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {},
                    errorMessage = "Incorrect PIN. Please try again."
                )
            }
        }

        composeTestRule.onNodeWithText("Incorrect PIN. Please try again.").assertIsDisplayed()
    }

    // ==================== Biometric Tests ====================

    @Test
    fun unlockMode_showsBiometricButton_whenAvailable() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = false,
                    isBiometricAvailable = true,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Use Biometric").assertExists()
    }

    @Test
    fun setupMode_hidesBiometricButton() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = true,
                    isBiometricAvailable = true,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Use Biometric").assertDoesNotExist()
    }

    // ==================== Navigation Tests ====================

    @Test
    fun backButton_callsCallback() {
        var backClicked = false

        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = false,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = { backClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assert(backClicked) { "onBackClick callback should be invoked" }
    }

    // ==================== Keypad Tests ====================

    @Test
    fun keypad_displaysDigits() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = false,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        // Verify all digits exist in the tree (some may be off-screen)
        for (i in 0..9) {
            composeTestRule.onNodeWithText("$i").assertExists()
        }
    }

    @Test
    fun keypad_topRowButtonsClickable() {
        composeTestRule.setContent {
            TestTheme {
                VaultAuthScreen(
                    isSetupMode = false,
                    isBiometricAvailable = false,
                    onPinSubmit = {},
                    onBiometricClick = {},
                    onBackClick = {}
                )
            }
        }

        // Top two rows are always visible
        composeTestRule.onNodeWithText("1").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("2").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("3").assertIsDisplayed().performClick()
    }
}

package ai.baseweight.sideeye.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isToggleable
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.baseweight.sideeye.data.ai.FlagCategory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple theme wrapper for tests that doesn't require Activity window access.
 */
@Composable
private fun TestTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Welcome Screen Tests ====================

    @Test
    fun welcomeScreen_displaysCorrectContent() {
        composeTestRule.setContent {
            TestTheme {
                WelcomeScreen(onGetStarted = {})
            }
        }

        // Verify welcome text is displayed
        composeTestRule.onNodeWithText("Welcome to SideEye").assertIsDisplayed()
        composeTestRule.onNodeWithText("Smart photo privacy protection powered by on-device AI")
            .assertIsDisplayed()

        // Verify feature list is shown
        composeTestRule.onNodeWithText("SideEye helps you:").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan photos for sensitive content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Store private photos in an encrypted vault").assertIsDisplayed()

        // Verify Get Started button exists
        composeTestRule.onNodeWithText("Get Started").assertIsDisplayed()
    }

    @Test
    fun welcomeScreen_getStartedButtonCallsCallback() {
        var callbackInvoked = false

        composeTestRule.setContent {
            TestTheme {
                WelcomeScreen(onGetStarted = { callbackInvoked = true })
            }
        }

        composeTestRule.onNodeWithText("Get Started").performClick()

        assert(callbackInvoked) { "onGetStarted callback should be invoked" }
    }

    // ==================== Model Download Screen Tests ====================

    @Test
    fun modelDownloadScreen_showsWaitingForWifi_whenNotOnWifi() {
        composeTestRule.setContent {
            TestTheme {
                ModelDownloadScreen(
                    isDownloading = false,
                    downloadProgress = 0f,
                    downloadError = null,
                    isDownloaded = false,
                    isOnWifi = false,
                    onDownload = {},
                    onContinue = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Waiting for WiFi").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download on Mobile Data").assertIsDisplayed()
    }

    @Test
    fun modelDownloadScreen_showsDownloadProgress_whenDownloading() {
        composeTestRule.setContent {
            TestTheme {
                ModelDownloadScreen(
                    isDownloading = true,
                    downloadProgress = 0.45f,
                    downloadError = null,
                    isDownloaded = false,
                    isOnWifi = true,
                    onDownload = {},
                    onContinue = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Downloading AI Model...").assertIsDisplayed()
        composeTestRule.onNodeWithText("45%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue Setup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download will continue in background").assertIsDisplayed()
    }

    @Test
    fun modelDownloadScreen_showsModelReady_whenDownloaded() {
        composeTestRule.setContent {
            TestTheme {
                ModelDownloadScreen(
                    isDownloading = false,
                    downloadProgress = 1f,
                    downloadError = null,
                    isDownloaded = true,
                    isOnWifi = true,
                    onDownload = {},
                    onContinue = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Model Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun modelDownloadScreen_showsError_whenDownloadFails() {
        composeTestRule.setContent {
            TestTheme {
                ModelDownloadScreen(
                    isDownloading = false,
                    downloadProgress = 0f,
                    downloadError = "Network connection failed",
                    isDownloaded = false,
                    isOnWifi = true,
                    onDownload = {},
                    onContinue = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Download Failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network connection failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry Download").assertIsDisplayed()
    }

    @Test
    fun modelDownloadScreen_continueCallsCallback_whenDownloading() {
        var continueClicked = false

        composeTestRule.setContent {
            TestTheme {
                ModelDownloadScreen(
                    isDownloading = true,
                    downloadProgress = 0.5f,
                    downloadError = null,
                    isDownloaded = false,
                    isOnWifi = true,
                    onDownload = {},
                    onContinue = { continueClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Continue Setup").performClick()

        assert(continueClicked) { "onContinue callback should be invoked" }
    }

    @Test
    fun modelDownloadScreen_downloadOnMobileData_callsDownload() {
        var downloadClicked = false

        composeTestRule.setContent {
            TestTheme {
                ModelDownloadScreen(
                    isDownloading = false,
                    downloadProgress = 0f,
                    downloadError = null,
                    isDownloaded = false,
                    isOnWifi = false,
                    onDownload = { downloadClicked = true },
                    onContinue = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Download on Mobile Data").performClick()

        assert(downloadClicked) { "onDownload callback should be invoked" }
    }

    // ==================== Vault Setup Screen Tests ====================

    @Test
    fun vaultSetupScreen_displaysCorrectContent() {
        composeTestRule.setContent {
            TestTheme {
                VaultSetupScreen(
                    onSetupComplete = {},
                    onSkip = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Set Up Vault PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("PIN (4-6 digits)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip for now").assertIsDisplayed()
    }

    @Test
    fun vaultSetupScreen_setPinButton_disabledWhenPinsTooShort() {
        composeTestRule.setContent {
            TestTheme {
                VaultSetupScreen(
                    onSetupComplete = {},
                    onSkip = {}
                )
            }
        }

        // Enter short PIN
        composeTestRule.onNodeWithText("PIN (4-6 digits)").performTextInput("12")
        composeTestRule.onNodeWithText("Confirm PIN").performTextInput("12")

        // Set PIN button should be disabled
        composeTestRule.onNodeWithText("Set PIN").assertIsNotEnabled()
    }

    @Test
    fun vaultSetupScreen_setPinButton_disabledWhenPinsDontMatch() {
        composeTestRule.setContent {
            TestTheme {
                VaultSetupScreen(
                    onSetupComplete = {},
                    onSkip = {}
                )
            }
        }

        // Enter mismatched PINs
        composeTestRule.onNodeWithText("PIN (4-6 digits)").performTextInput("1234")
        composeTestRule.onNodeWithText("Confirm PIN").performTextInput("5678")

        // Set PIN button should be disabled
        composeTestRule.onNodeWithText("Set PIN").assertIsNotEnabled()
        composeTestRule.onNodeWithText("PINs don't match").assertIsDisplayed()
    }

    @Test
    fun vaultSetupScreen_setPinButton_enabledWhenPinsMatch() {
        composeTestRule.setContent {
            TestTheme {
                VaultSetupScreen(
                    onSetupComplete = {},
                    onSkip = {}
                )
            }
        }

        // Enter matching PINs
        composeTestRule.onNodeWithText("PIN (4-6 digits)").performTextInput("1234")
        composeTestRule.onNodeWithText("Confirm PIN").performTextInput("1234")

        // Set PIN button should be enabled
        composeTestRule.onNodeWithText("Set PIN").assertIsEnabled()
    }

    @Test
    fun vaultSetupScreen_skipCallsCallback() {
        var skipClicked = false

        composeTestRule.setContent {
            TestTheme {
                VaultSetupScreen(
                    onSetupComplete = {},
                    onSkip = { skipClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Skip for now").performClick()

        assert(skipClicked) { "onSkip callback should be invoked" }
    }

    @Test
    fun vaultSetupScreen_setupCompleteCallsCallback_withPin() {
        var receivedPin: String? = null

        composeTestRule.setContent {
            TestTheme {
                VaultSetupScreen(
                    onSetupComplete = { pin -> receivedPin = pin },
                    onSkip = {}
                )
            }
        }

        // Enter matching PINs
        composeTestRule.onNodeWithText("PIN (4-6 digits)").performTextInput("9876")
        composeTestRule.onNodeWithText("Confirm PIN").performTextInput("9876")

        // Click Set PIN
        composeTestRule.onNodeWithText("Set PIN").performClick()

        assert(receivedPin == "9876") { "onSetupComplete should receive the PIN" }
    }

    // ==================== Categories Screen Tests ====================

    @Test
    fun categoriesScreen_displaysAllCategories() {
        composeTestRule.setContent {
            TestTheme {
                CategoriesScreen(
                    enabledCategories = emptySet(),
                    onToggleCategory = {},
                    onComplete = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Choose Categories").assertIsDisplayed()

        // Verify all categories are displayed
        FlagCategory.entries.forEach { category ->
            composeTestRule.onNodeWithText(category.displayName).assertIsDisplayed()
        }
    }

    @Test
    fun categoriesScreen_completeButtonDisabled_whenNoCategoriesSelected() {
        composeTestRule.setContent {
            TestTheme {
                CategoriesScreen(
                    enabledCategories = emptySet(),
                    onToggleCategory = {},
                    onComplete = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Select at least one category to continue").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Using SideEye").assertIsNotEnabled()
    }

    @Test
    fun categoriesScreen_completeButtonEnabled_whenCategorySelected() {
        composeTestRule.setContent {
            TestTheme {
                CategoriesScreen(
                    enabledCategories = setOf(FlagCategory.NUDITY),
                    onToggleCategory = {},
                    onComplete = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Start Using SideEye").assertIsEnabled()
    }

    @Test
    fun categoriesScreen_toggleCategoryCallsCallback() {
        var toggledCategory: FlagCategory? = null

        composeTestRule.setContent {
            TestTheme {
                CategoriesScreen(
                    enabledCategories = setOf(FlagCategory.NUDITY),
                    onToggleCategory = { toggledCategory = it },
                    onComplete = {}
                )
            }
        }

        // Find the row with "Drugs" and click the switch within it
        // The Switch is a sibling of the text, so we find all switches and click the one for Drugs
        // Since the Drugs category is at index 2 (0=NUDITY, 1=SUGGESTIVE, 2=DRUGS, 3=EMBARRASSING)
        composeTestRule.onAllNodes(isToggleable())[2].performClick()

        assert(toggledCategory == FlagCategory.DRUGS) { "onToggleCategory should be called with DRUGS" }
    }

    @Test
    fun categoriesScreen_completeCallsCallback() {
        var completeClicked = false

        composeTestRule.setContent {
            TestTheme {
                CategoriesScreen(
                    enabledCategories = setOf(FlagCategory.NUDITY),
                    onToggleCategory = {},
                    onComplete = { completeClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Start Using SideEye").performClick()

        assert(completeClicked) { "onComplete callback should be invoked" }
    }
}

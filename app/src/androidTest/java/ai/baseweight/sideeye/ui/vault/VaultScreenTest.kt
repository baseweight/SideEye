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
class VaultScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyVault_displaysEmptyMessage() {
        composeTestRule.setContent {
            TestTheme {
                VaultScreen(
                    onBackClick = {},
                    onSettingsClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Your private vault is empty").assertIsDisplayed()
    }

    @Test
    fun vaultScreen_displaysTitle() {
        composeTestRule.setContent {
            TestTheme {
                VaultScreen(
                    onBackClick = {},
                    onSettingsClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Private Vault").assertIsDisplayed()
    }

    @Test
    fun vaultScreen_backButtonCallsCallback() {
        var backClicked = false

        composeTestRule.setContent {
            TestTheme {
                VaultScreen(
                    onBackClick = { backClicked = true },
                    onSettingsClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assert(backClicked) { "onBackClick callback should be invoked" }
    }

    @Test
    fun vaultScreen_hasLockButton() {
        composeTestRule.setContent {
            TestTheme {
                VaultScreen(
                    onBackClick = {},
                    onSettingsClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Lock Vault").assertIsDisplayed()
    }

    @Test
    fun vaultScreen_hasSettingsInMenu() {
        composeTestRule.setContent {
            TestTheme {
                VaultScreen(
                    onBackClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // Open overflow menu
        composeTestRule.onNodeWithContentDescription("More options").performClick()

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun vaultScreen_settingsCallsCallback() {
        var settingsClicked = false

        composeTestRule.setContent {
            TestTheme {
                VaultScreen(
                    onBackClick = {},
                    onSettingsClick = { settingsClicked = true }
                )
            }
        }

        // Open overflow menu and click settings
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Settings").performClick()

        assert(settingsClicked) { "onSettingsClick callback should be invoked" }
    }
}

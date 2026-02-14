package ai.baseweight.sideeye.ui.about

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
class AboutScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aboutScreen_displaysAppName() {
        composeTestRule.setContent {
            TestTheme {
                AboutScreen(onBackClick = {})
            }
        }

        composeTestRule.onNodeWithText("SideEye").assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysVersion() {
        composeTestRule.setContent {
            TestTheme {
                AboutScreen(onBackClick = {})
            }
        }

        composeTestRule.onNodeWithText("Version 1.0").assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysAttribution() {
        composeTestRule.setContent {
            TestTheme {
                AboutScreen(onBackClick = {})
            }
        }

        composeTestRule.onNodeWithText("by Baseweight").assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysTitle() {
        composeTestRule.setContent {
            TestTheme {
                AboutScreen(onBackClick = {})
            }
        }

        composeTestRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun aboutScreen_displaysLegalLinks() {
        composeTestRule.setContent {
            TestTheme {
                AboutScreen(onBackClick = {})
            }
        }

        composeTestRule.onNodeWithText("Terms of Service").assertExists()
        composeTestRule.onNodeWithText("Privacy Policy").assertExists()
    }

    @Test
    fun aboutScreen_displaysMascotImage() {
        composeTestRule.setContent {
            TestTheme {
                AboutScreen(onBackClick = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Baseweight mascot").assertIsDisplayed()
    }

    @Test
    fun aboutScreen_backButtonCallsCallback() {
        var backClicked = false

        composeTestRule.setContent {
            TestTheme {
                AboutScreen(onBackClick = { backClicked = true })
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assert(backClicked) { "onBackClick callback should be invoked" }
    }
}

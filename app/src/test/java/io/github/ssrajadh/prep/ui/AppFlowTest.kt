package io.github.ssrajadh.prep.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ssrajadh.prep.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app on the JVM with Robolectric (a simulated Android runtime) and
 * saves screenshots to app/build/outputs/roborazzi/. Runs with the bundled content and
 * a fresh, empty database each time.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    private fun waitForText(text: String, substring: Boolean = true) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test fun reviewFlow() {
        waitForText("for today")
        shot("01-today")

        compose.onNodeWithText("Start").performClick()
        waitForText("Reveal key points")
        compose.onNodeWithText("1 of", substring = true).assertExists()
        shot("02-prompt")

        compose.onNodeWithText("Reveal key points").performClick()
        waitForText("Tick what you covered")
        shot("03-revealed")

        // Interaction past this point is covered by ReviewLoopTest (no screenshots there).
    }

    @Test fun progressAndSettings() {
        waitForText("for today")
        compose.onNodeWithContentDescription("Progress").performClick()
        waitForText("System Design")
        shot("06-progress")

        compose.onNodeWithText("Caching").performClick()
        waitForText("Why cache", substring = true)
        shot("07-topic")
        compose.onAllNodesWithText("Why cache", substring = true).onFirst().performClick()
        waitForText("Section:", substring = true)
        shot("08-card-detail")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Active tracks")
        shot("09-settings")
        compose.onNodeWithText("About").performScrollTo()
        shot("10-settings-bottom")
    }
}

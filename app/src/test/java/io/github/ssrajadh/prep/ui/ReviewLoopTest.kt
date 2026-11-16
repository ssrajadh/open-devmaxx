package io.github.ssrajadh.prep.ui

import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.ssrajadh.prep.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The review loop: start, reveal, rate, advance, tick, close, and Today updates.
 * Runs without native graphics (no screenshots): under Robolectric's native mode a
 * button that stays on screen after a tap never settles. Opening a dialog inside the
 * edge-to-edge activity also never settles under Robolectric, so the flag dialog is
 * tested on its own (FlagDialogTest) and flag storage in RepositoryTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class ReviewLoopTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun rateAdvancesAndPersists() {
        waitForText("5 cards for today")
        compose.onNodeWithText("Start").performClick()
        waitForText("1 of 5")
        compose.onNodeWithText("Reveal key points").performClick()
        waitForText("suggested: Again")
        compose.onNodeWithText("Good").performClick()
        waitForText("2 of 5")

        compose.onNodeWithText("Reveal key points").performClick()
        waitForText("0 of")
        compose.onAllNodes(isToggleable())[0].performClick()
        waitForText("1 of")

        compose.onNodeWithContentDescription("Close").performClick()
        waitForText("4 cards for today")
        compose.onNodeWithText("Continue").assertExists()
    }
}

package io.github.ssrajadh.prep.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * The quiz loop in the real activity: answer a card's questions, see results, rate,
 * advance, close, and Today updates. Runs without native graphics (no screenshots).
 * Opening a dialog inside the edge-to-edge activity never settles under Robolectric, so
 * the flag dialog is tested on its own (FlagDialogTest) and flag storage in RepositoryTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class ReviewLoopTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun quizRateAdvanceAndClose() {
        compose.waitForText("5 cards for today")
        compose.onNodeWithText("Start").performClick()
        compose.waitForText("Card 1 of 5")
        compose.waitForText("Question 1 of")

        compose.answerWholeCard()
        compose.waitForText("Suggested:")
        compose.onNodeWithText("Good").performClick()
        compose.waitForText("Card 2 of 5")
        compose.waitForText("Question 1 of")

        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitForText("4 cards for today")
        compose.onNodeWithText("Continue").assertExists()
    }
}

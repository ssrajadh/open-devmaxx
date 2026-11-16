package io.github.ssrajadh.prep.ui

import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 10_000) =
    waitUntil(timeoutMs) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

private fun ComposeTestRule.has(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** Answers one question by picking the first option shown, then moves on. */
fun ComposeTestRule.answerCurrentQuestion() {
    if (has("Check")) {
        onAllNodes(isToggleable())[0].performClick()
        onNodeWithText("Check").performClick()
    } else {
        onAllNodes(isSelectable())[0].performClick()
    }
    waitUntil(5_000) { has("Next question") || has("See results") }
}

/** Answers every question of the current card and opens its results. */
fun ComposeTestRule.answerWholeCard() {
    while (true) {
        answerCurrentQuestion()
        if (has("See results")) {
            onNodeWithText("See results").performClick()
            waitForText("correct")
            return
        }
        onNodeWithText("Next question").performClick()
        waitForText("Question")
    }
}

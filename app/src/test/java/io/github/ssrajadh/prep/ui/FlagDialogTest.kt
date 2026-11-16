package io.github.ssrajadh.prep.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.ssrajadh.prep.ui.components.FlagDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlagDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun submitsChosenReason() {
        var got: String? = null
        compose.setContent { FlagDialog(onDismiss = {}, onSubmit = { r, _ -> got = r }) }
        compose.onNodeWithText("Unclear").performClick()
        compose.onNodeWithText("Flag").performClick()
        assertEquals("Unclear", got)
    }
}

package io.github.ssrajadh.prep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ssrajadh.prep.ui.progress.CardDetailScreen
import io.github.ssrajadh.prep.ui.progress.ProgressScreen
import io.github.ssrajadh.prep.ui.progress.TopicScreen
import io.github.ssrajadh.prep.ui.review.ReviewScreen
import io.github.ssrajadh.prep.ui.settings.SettingsScreen
import io.github.ssrajadh.prep.ui.theme.PrepTheme
import io.github.ssrajadh.prep.ui.today.TodayScreen

/**
 * The single Activity. With Compose, screens are composable functions and the
 * NavHost swaps between them; there are no Fragments or XML layouts.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PrepTheme { AppNav() } }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    // No enter/exit animations: screens switch instantly.
    NavHost(
        nav,
        startDestination = "today",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable("today") {
            TodayScreen(
                onStart = { nav.navigate("review") },
                onProgress = { nav.navigate("progress") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("review") { ReviewScreen(onClose = { nav.popBackStack() }) }
        composable("progress") {
            ProgressScreen(onBack = { nav.popBackStack() }, onTopic = { nav.navigate("topic/$it") })
        }
        composable("topic/{topicId}", arguments = listOf(navArgument("topicId") { type = NavType.StringType })) {
            TopicScreen(
                topicId = it.arguments?.getString("topicId").orEmpty(),
                onBack = { nav.popBackStack() },
                onCard = { id -> nav.navigate("card/$id") },
            )
        }
        composable("card/{cardId}", arguments = listOf(navArgument("cardId") { type = NavType.StringType })) {
            CardDetailScreen(cardId = it.arguments?.getString("cardId").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}

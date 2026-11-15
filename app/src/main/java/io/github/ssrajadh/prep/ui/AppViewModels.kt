package io.github.ssrajadh.prep.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.ssrajadh.prep.PrepApp

/** Access the app-wide singletons from a composable (used to construct ViewModels). */
@Composable
fun prepApp(): PrepApp = LocalContext.current.applicationContext as PrepApp

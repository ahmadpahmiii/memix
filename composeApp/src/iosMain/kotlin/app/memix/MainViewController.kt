package app.memix

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App() }

/** Swift can't pass Kotlin default arguments, so iOS starts Koin through this. */
fun startKoinOnIos() = initKoin()

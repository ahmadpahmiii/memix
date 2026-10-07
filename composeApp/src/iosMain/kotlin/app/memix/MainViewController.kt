package app.memix

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
fun MainViewController() = ComposeUIViewController { App(isDebugBuild = Platform.isDebugBinary) }

/** Swift can't pass Kotlin default arguments, so iOS starts Koin through this. */
fun startKoinOnIos() = initKoin()

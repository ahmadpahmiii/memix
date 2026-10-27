package app.memix.core.designsystem

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
internal actual fun isReduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()

package app.memix.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// Values mirror design/tokens.json; change them there first (design-tokens skill).

object MemixColors {
    val canvas = Color(0xFF111111)
    val stage = Color(0xFF000000)
    val surface = Color(0xFF1B1B1B)
    val surfaceRaised = Color(0xFF2C2C2C)
    val hairline = Color(0xFF393939)
    val text = Color(0xFFF2F2F2)
    val textSecondary = Color(0xFFA6A6A6)
    val textMuted = Color(0xFF949494)
    val primary = Color(0xFF2BB3F3)
    val primaryPressed = Color(0xFF189BD8)
    val primarySubtle = Color(0x292BB3F3)
    val onPrimary = Color(0xFF111111)
    val selection = Color(0xFFFFFFFF)
    val trackVideo = Color(0xFF3D3D3D)
    val trackText = Color(0xFFE78E40)
    val trackSticker = Color(0xFFD8AE31)
    val trackMemeSound = Color(0xFF2BB3F3)
    val trackAudio = Color(0xFF3DAE79)
    val trackEffect = Color(0xFFAA84EB)
    val onTrack = Color(0xFF111111)
    val danger = Color(0xFFF6574C)
    val dangerPressed = Color(0xFFE04E43)
    val success = Color(0xFF3BCE7B)
    val focusRing = Color(0xFFFFFFFF)
    val scrim = Color(0xB3000000)
}

object MemixSpacing {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val space10 = 40.dp
}

object MemixShapes {
    val radiusSm = RoundedCornerShape(6.dp)
    val radiusMd = RoundedCornerShape(10.dp)
    val radiusLg = RoundedCornerShape(16.dp)
    val radiusLgTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val radiusFull = RoundedCornerShape(percent = 50)
}

object MemixStroke {
    val strokeHairline = 1.dp
    val strokeSelection = 2.dp
    val strokeFocus = 2.dp
}

object MemixSize {
    val touchTarget = 48.dp
    val navHeight = 64.dp
    val toolbarHeight = 72.dp
    val trackHeightVideo = 40.dp
    val trackHeight = 28.dp
    val playheadWidth = 2.dp
}

object MemixElevation {
    val shadowSheet = Shadow(radius = 24.dp, color = Color(0x80000000), offset = DpOffset(0.dp, (-8).dp))
    val shadowFloat = Shadow(radius = 24.dp, color = Color(0x66000000), offset = DpOffset(0.dp, 8.dp))
}

object MemixMotion {
    const val durationPress = 120
    const val durationSheet = 200
    const val durationScreen = 320
    const val durationBonk = 240
}

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
    val toolItemWidth = 64.dp
    val trackHeightVideo = 40.dp
    val trackHeight = 28.dp
    val playheadWidth = 2.dp
    val playheadHeadSize = 12.dp
    val trimHandleWidth = 12.dp
    val iconSmall = 16.dp
    val railHeight = 4.dp

    /** The white scale-and-rotate handle on a selected caption on the preview; its target is still [touchTarget]. */
    val canvasHandleSize = 24.dp
    /** A censor's edge handle: a white bar 20 dp long and 6 dp thick in the middle of each edge (P2-05). */
    val edgeHandleLength = 20.dp
    val edgeHandleThickness = 6.dp
    val filterTileSize = 72.dp

    /** A color swatch's circle, drawn in a [touchTarget] square. */
    val swatchSize = 32.dp

    /** The play disc over a video that isn't playing (the Exported screen, P1-12); `play-disc-size` in design/tokens.json. */
    val playDiscSize = 56.dp

    /** The square thumbnail at the start of a list row (a draft's first frame, P1-14); `list-thumb-size` in design/tokens.json. */
    val listThumbSize = 56.dp

    /** The square thumbnail at the start of a LayerRow in the photo editor's Layers panel (P2-01); `layer-thumb-size` in design/tokens.json. */
    val layerThumbSize = 40.dp

    /** The square icon chip over a picture (the type badge on a draft's thumbnail, P1-14); `badge-size` in design/tokens.json. */
    val badge = 20.dp

    /** A medium glyph: the search field's magnifier, the glyphs in SoundRow's play disc and Use block (P3-02); `icon-medium` in design/tokens.json. */
    val iconMedium = 20.dp

    /** SoundRow's play disc and its loading placeholder (P3-02); `sound-disc-size` in design/tokens.json. */
    val soundDiscSize = 40.dp

    /** One waveform bar's width, and the gap between bars (P3-03); `waveform-bar-width` and `waveform-bar-gap` in design/tokens.json. */
    val waveformBarWidth = 2.dp
    val waveformBarGap = 2.dp

    /** A waveform's height in a SoundRow and in the details sheet (P3-03); `waveform-height` and `waveform-height-large`. */
    val waveformHeight = 24.dp
    val waveformHeightLarge = 56.dp

    /** Opacity of the on-track waveform inside a meme sound or audio clip (P3-09); `waveform-clip-opacity`. */
    const val waveformClipOpacity = 0.6f

    /** A row on the WhatsApp Packs screen (P2-09), its minimum height; `pack-row-height` in design/tokens.json. */
    val packRowHeight = 72.dp
    /** A row in the Emojis sheet of the pack builder (P2-09); `emoji-row-height` in design/tokens.json. */
    val emojiRowHeight = 64.dp
    /** The tray icon on the Added page (P2-09); `pack-tray-size` in design/tokens.json. */
    val packTraySize = 96.dp
    /** The minimum width of a sticker grid tile (P2-14, P2-09); `grid-tile-min` in design/tokens.json. */
    val gridTileMin = 96.dp
    /** A full-width row that opens another screen, such as "WhatsApp packs" in the Stickers sheet (P2-09); `nav-row-height` in design/tokens.json. */
    val navRowHeight = 56.dp
    /** Share of the window height a content sheet may use before its content scrolls; `sheet-max-height-fraction` in design/tokens.json. */
    const val sheetMaxHeightFraction = 0.7f
    /** Share of the window height the pack builder's pinned bottom area may take at large font; `pinned-bar-max-fraction` in design/tokens.json. */
    const val pinnedBarMaxFraction = 0.4f
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

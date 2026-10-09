package app.memix.core.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.Chip
import app.memix.core.designsystem.component.CloseButton
import app.memix.core.designsystem.component.EntryCard
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.Menu
import app.memix.core.designsystem.component.MenuItem
import app.memix.core.designsystem.component.ProgressBar
import app.memix.core.designsystem.component.SegmentedTabs
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.SoundRow
import app.memix.core.designsystem.component.Tabs
import app.memix.core.designsystem.component.TemplateCard
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToastAction
import app.memix.core.designsystem.component.ToastDismiss
import app.memix.core.designsystem.component.Toggle
import app.memix.core.designsystem.usesHindiDisplayFace
import kotlin.math.roundToInt

// Debug-only screen for the designer's review (P0-04). Labels are developer English, never translated,
// and every name or number here is a placeholder, not catalog data.

/** [appSections] adds sections from outside the design system, such as a feature's sheet states (debug only). */
@Composable
fun ComponentCatalog(onClose: () -> Unit, appSections: @Composable ColumnScope.() -> Unit = {}) {
    var sheetOpen by remember { mutableStateOf(false) }
    var blockingSheetOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MemixColors.canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space8),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Component catalog", MemixTheme.type.titleL, Modifier.weight(1f))
                CloseButton("Close", onClose)
            }
            Text(
                "Debug builds only. Press Tab on a hardware keyboard to see focus rings.",
                MemixTheme.type.body,
                color = MemixColors.textSecondary,
            )
            TypeSection()
            ButtonSection(onOpenSheet = { sheetOpen = true }, onOpenBlockingSheet = { blockingSheetOpen = true })
            ChipSection()
            SoundRowSection()
            CardSection()
            TabSection()
            SliderAndToggleSection()
            ProgressBarSection()
            IconButtonSection()
            ToastSection()
            MenuSection()
            IconSection()
            appSections()
        }
        Sheet(visible = sheetOpen, onDismiss = { sheetOpen = false }, title = "Sheet title") {
            var first by remember { mutableStateOf(true) }
            var second by remember { mutableStateOf(false) }
            Toggle("Setting one", first, { first = it })
            Toggle("Setting two", second, { second = it })
            Button("Done", { sheetOpen = false }, Modifier.fillMaxWidth(), ButtonVariant.Primary)
        }
        // Blocking variant: no grabber, no close button; scrim taps and drags do nothing, back runs Cancel.
        Sheet(visible = blockingSheetOpen, onDismiss = { blockingSheetOpen = false }, title = "Blocking sheet title", blocking = true) {
            Text("Status line", MemixTheme.type.label, color = MemixColors.textSecondary)
            ProgressBar(0.4f, "40%")
            Button("Cancel", { blockingSheetOpen = false }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Section(name: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
        Text(name, MemixTheme.type.title)
        content()
    }
}

@Composable
private fun TypeSection() = Section("Type") {
    val type = MemixTheme.type
    listOf<Pair<String, TextStyle>>(
        "display-xl" to type.displayXl, "display" to type.display, "Memix (wordmark)" to type.wordmark,
        "meme-caption" to type.memeCaption, "title-l" to type.titleL, "title" to type.title, "body" to type.body,
        "body-strong" to type.bodyStrong, "label" to type.label, "caption" to type.caption,
        "timecode 0:02.40" to type.timecode,
    ).forEach { (name, style) -> Text(name, style) }
    // In Hindi the two display roles switch to Teko Bold (P1-17); these samples show its Devanagari.
    if (usesHindiDisplayFace()) {
        Text("मीम बनाएं (display-xl)", type.displayXl)
        Text("वीडियो मीम (display)", type.display)
    }
}

@Composable
private fun ButtonSection(onOpenSheet: () -> Unit, onOpenBlockingSheet: () -> Unit) = Section("Button") {
    Button("Open sheet", onOpenSheet, variant = ButtonVariant.Primary)
    Button("Open blocking sheet", onOpenBlockingSheet)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
        Button("Secondary", {})
        Button("Delete draft", {}, variant = ButtonVariant.Danger)
        Button("Cancel", {}, variant = ButtonVariant.Ghost)
        Button("See all", {}, variant = ButtonVariant.Quiet)
        Button("Make a meme", {}, leadingIcon = MemixIcons.Create)
    }
}

@Composable
private fun ChipSection() = Section("Chip") {
    var selected by remember { mutableIntStateOf(0) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        listOf("All", "Category one", "Category two", "Category three", "Category four").forEachIndexed { index, label ->
            Chip(label, selected == index, { selected = index })
        }
    }
}

@Composable
private fun SoundRowSection() = Section("SoundRow") {
    var playing by remember { mutableIntStateOf(1) }
    val favorites = remember { mutableStateOf(setOf(1)) }
    listOf("Sound name", "Sound name, playing").forEachIndexed { index, name ->
        SoundRow(
            name = name,
            meta = "0:00 · Category",
            isPlaying = playing == index,
            isFavorite = index in favorites.value,
            onPlayToggle = { playing = if (playing == index) -1 else index },
            onFavoriteChange = { on -> favorites.value = if (on) favorites.value + index else favorites.value - index },
            onAdd = {},
        )
    }
}

@Composable
private fun CardSection() = Section("EntryCard and TemplateCard") {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
        EntryCard("Video meme", "Clips, sounds, effects", MemixIcons.Video, emphasized = true, onClick = {}, modifier = Modifier.weight(1f).fillMaxHeight())
        EntryCard("Photo meme", "Captions, panels, cut-outs", MemixIcons.Photo, emphasized = false, onClick = {}, modifier = Modifier.weight(1f).fillMaxHeight())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
        TemplateCard("Template name", "Preset", {}, Modifier.weight(1f))
        TemplateCard("Template name that runs long", "Green screen", {}, Modifier.weight(1f))
    }
}

@Composable
private fun TabSection() = Section("Tabs and SegmentedTabs") {
    var tab by remember { mutableIntStateOf(0) }
    Tabs(listOf("Tab one", "Tab two", "Tab three", "Tab four", "Tab five"), tab, { tab = it })
    var segment by remember { mutableIntStateOf(0) }
    SegmentedTabs(listOf("Video", "Photo"), segment, { segment = it }, Modifier.width(240.dp))
}

@Composable
private fun SliderAndToggleSection() = Section("Slider and Toggle") {
    var volume by remember { mutableFloatStateOf(0.8f) }
    Slider("Volume", volume, { volume = it }, "${(volume * 100).roundToInt()}%", defaultValue = 1f)
    var on by remember { mutableStateOf(false) }
    Toggle("Show watermark preview", on, { on = it })
}

@Composable
private fun ProgressBarSection() = Section("ProgressBar") {
    var progress by remember { mutableFloatStateOf(0.4f) }
    listOf(0f, 1f).forEach { fixed -> ProgressBar(fixed, "${(fixed * 100).roundToInt()}%") }
    ProgressBar(progress, "${(progress * 100).roundToInt()}%")
    Row(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
        Button("Back 10%", { progress = (progress - 0.1f).coerceAtLeast(0f) })
        Button("Forward 10%", { progress = (progress + 0.1f).coerceAtMost(1f) })
    }
}

@Composable
private fun IconButtonSection() = Section("IconButton") {
    var playing by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        IconButton(MemixIcons.Close, "Close", {})
        IconButton(if (playing) MemixIcons.Pause else MemixIcons.Play, if (playing) "Pause" else "Play", { playing = !playing })
        IconButton(MemixIcons.Undo, "Undo", {})
        IconButton(MemixIcons.Redo, "Redo", {}, enabled = false)
    }
}

@Composable
private fun ToastSection() = Section("Toast") {
    Toast("Undo: Split")
    Toast("To split, move the playhead inside this clip. A long message wraps as far as it needs to and is never cut off.")
    var bannerShown by remember { mutableStateOf(true) }
    FadingToast(if (bannerShown) "Your phone is full, so your latest changes aren't saved." else null) { message ->
        Toast(message, action = ToastAction("Free up space") {}, dismiss = ToastDismiss("Dismiss") { bannerShown = false })
    }
    Button("Show the persistent variant again", { bannerShown = true })
    Toast("Persistent, no action: your latest changes aren't saved yet. Memix tries again with your next change.", dismiss = ToastDismiss("Dismiss") {})
}

// The timeline's zoom menu (P1-05): opens where the button is pressed; the middle item shows the "at its limit" state.
@Composable
private fun MenuSection() = Section("Menu") {
    var open by remember { mutableStateOf(false) }
    var steps by remember { mutableIntStateOf(0) }
    Box {
        Button("Open the menu (zoom steps: $steps)", { open = true })
        Menu(
            expanded = open,
            pressPoint = IntOffset.Zero,
            title = "Timeline",
            items = {
                listOf(
                    MenuItem("Zoom in", { steps++ }, closesMenu = false),
                    MenuItem("Zoom out", {}, enabled = false),
                    MenuItem("Show whole video", { steps = 0 }),
                )
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun IconSection() = Section("Icons") {
    val icons = listOf(
        "home" to MemixIcons.Home, "templates" to MemixIcons.Templates, "create" to MemixIcons.Create,
        "sounds" to MemixIcons.Sounds, "drafts" to MemixIcons.Drafts, "video" to MemixIcons.Video,
        "photo" to MemixIcons.Photo, "close" to MemixIcons.Close, "chevron_right" to MemixIcons.ChevronRight,
        "arrow_back" to MemixIcons.ArrowBack, "globe" to MemixIcons.Globe, "play" to MemixIcons.Play,
        "pause" to MemixIcons.Pause, "heart" to MemixIcons.Heart, "heart_filled" to MemixIcons.HeartFilled,
        "undo" to MemixIcons.Undo, "redo" to MemixIcons.Redo,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space4), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
        icons.forEach { (name, icon) ->
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space1)) {
                    Icon(icon, name, MemixColors.textSecondary)
                    Icon(icon, null, MemixColors.text)
                    Icon(icon, null, MemixColors.primary)
                }
                Text(name, MemixTheme.type.caption, color = MemixColors.textSecondary)
            }
        }
    }
}

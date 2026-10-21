package app.memix.core.domain.project

import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Project
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.Transform
import app.memix.core.model.project.TrackKind
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Caption edits (spec docs/ux/specs/P1-10-text.md → Interactions, Position panel). Like every edit, each returns a new
 * Project, or this one unchanged when it can't apply. Captions keep their own times: main-track edits never move them.
 */

/** A new caption lasts this long, or to the end of the video, whichever comes first (spec → Landing). */
const val TEXT_LANDING_US = 3_000_000L

/** A landing shorter than this is moved so it ends at the end of the video instead of flashing by. */
private const val MIN_TEXT_LANDING_US = 1_000_000L

/** Where a new caption sits vertically, first free slot first: top, bottom, middle (fractions of the canvas height). */
private val LANDING_SLOTS = listOf(0.25f, 0.75f, 0.5f)

/** A slot is taken when a caption visible at the playhead has its center this close to it. */
private const val SLOT_TAKEN_WITHIN = 0.1f

/** The Position panel's size steps, as scales (spec → Position panel). 100% is always one of them. */
val TEXT_SIZE_STEPS: List<Float> = listOf(0.25f, 0.33f, 0.5f, 0.67f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f)

/** The Position panel turns to the next multiple of this, so 0° is always reachable. */
const val TEXT_ROTATION_STEP_DEGREES = 15f

/** One Move arrow tap: 1% of the canvas width (left, right) or height (up, down). */
const val TEXT_MOVE_STEP = 0.01f

/**
 * Where a caption placed at the playhead [atUs] goes in time: from there for [TEXT_LANDING_US] or to the end of a video
 * [videoLengthUs] long; if that leaves under a second, it ends at the end instead. Returns (start, duration) in µs.
 */
fun textLandingSpan(atUs: Long, videoLengthUs: Long): Pair<Long, Long> {
    if (videoLengthUs <= 0) return 0L to TEXT_LANDING_US
    val startUs = atUs.coerceIn(0, videoLengthUs)
    if (videoLengthUs - startUs >= MIN_TEXT_LANDING_US) return startUs to minOf(TEXT_LANDING_US, videoLengthUs - startUs)
    val endStartUs = (videoLengthUs - TEXT_LANDING_US).coerceAtLeast(0)
    return endStartUs to minOf(TEXT_LANDING_US, videoLengthUs - endStartUs)
}

/** The first landing slot (top, bottom, middle) that no caption visible at [atUs] sits on; the middle when all are. */
fun Project.textLandingCenterY(atUs: Long): Float {
    val visible = textsAt(atUs).map { it.transform.centerY }
    return LANDING_SLOTS.firstOrNull { slot -> visible.none { abs(it - slot) < SLOT_TAKEN_WITHIN } } ?: LANDING_SLOTS.last()
}

/**
 * Adds caption [text] as [itemId] at the playhead [atUs] (see [textLandingSpan], [textLandingCenterY]) in [style],
 * centered horizontally at 100% and 0°, on the first text lane free for its whole span, else a new lane [newTrackId]
 * under the last. Blank text adds nothing.
 */
fun Project.addText(text: String, style: CaptionStyle, atUs: Long, itemId: String, newTrackId: String): Project {
    if (text.isBlank()) return this
    val (startUs, durationUs) = textLandingSpan(atUs, videoLengthUs())
    val item = TextItem(itemId, startUs, durationUs, text, style, Transform(centerX = 0.5f, centerY = textLandingCenterY(atUs)))
    return withItemOnFreeLane(item, TrackKind.TEXT, tracks().filter { it.kind == TrackKind.TEXT }, newTrackId)
}

/** New words for caption [itemId]; cleared to blank, the caption is deleted (spec → Typing → commit). */
fun Project.editText(itemId: String, text: String): Project {
    val item = textItem(itemId) ?: return this
    if (text.isBlank()) return deleteItem(itemId)
    if (text == item.text) return this
    return replaceText(item.copy(text = text))
}

/** Caption [itemId]'s font, color and outline. */
fun Project.setCaptionStyle(itemId: String, style: CaptionStyle): Project {
    val item = textItem(itemId) ?: return this
    return if (item.style == style) this else replaceText(item.copy(style = style))
}

/** Caption [itemId]'s place, size and turn on the frame, kept inside the limits ([clampedForText]). */
fun Project.setTextTransform(itemId: String, transform: Transform): Project {
    val item = textItem(itemId) ?: return this
    val clamped = transform.clampedForText()
    return if (item.transform == clamped) this else replaceText(item.copy(transform = clamped))
}

/** Caption [itemId], or null when it isn't one. */
fun Project.textItem(itemId: String): TextItem? = findItem(itemId)?.item as? TextItem

/** The captions shown at [atUs], in draw order: lane by lane from the first, so a lower lane draws on top. */
fun Project.textsAt(atUs: Long): List<TextItem> =
    tracks().filter { it.kind == TrackKind.TEXT }.flatMap { track ->
        track.items.filterIsInstance<TextItem>().filter { atUs >= it.startUs && atUs < it.startUs + it.durationUs }
    }

/**
 * The center stays on the canvas (0…1), the scale within [CaptionCatalog.MIN_SCALE]…[CaptionCatalog.MAX_SCALE], and the
 * turn within −180°…180°.
 */
fun Transform.clampedForText(): Transform = Transform(
    centerX = centerX.coerceIn(0f, 1f),
    centerY = centerY.coerceIn(0f, 1f),
    scale = scale.coerceIn(CaptionCatalog.MIN_SCALE, CaptionCatalog.MAX_SCALE),
    rotationDegrees = normalizedDegrees(rotationDegrees),
)

/** The next size step strictly above [scale], or null at the biggest size. */
fun nextTextSizeUp(scale: Float): Float? = TEXT_SIZE_STEPS.firstOrNull { it > scale + SCALE_TOLERANCE }

/** The next size step strictly below [scale], or null at the smallest size. */
fun nextTextSizeDown(scale: Float): Float? = TEXT_SIZE_STEPS.lastOrNull { it < scale - SCALE_TOLERANCE }

/** [degrees] turned to the next multiple of [TEXT_ROTATION_STEP_DEGREES]: clockwise when [clockwise], else back. */
fun nextTextRotation(degrees: Float, clockwise: Boolean): Float {
    val steps = degrees / TEXT_ROTATION_STEP_DEGREES
    val next = if (clockwise) floor(steps + STEP_TOLERANCE) + 1 else ceil(steps - STEP_TOLERANCE) - 1
    return normalizedDegrees(next * TEXT_ROTATION_STEP_DEGREES)
}

/** [degrees] as −180° (exclusive) to 180°. */
fun normalizedDegrees(degrees: Float): Float {
    var value = degrees % FULL_TURN
    if (value > HALF_TURN) value -= FULL_TURN
    if (value <= -HALF_TURN) value += FULL_TURN
    return value
}

private fun Project.replaceText(item: TextItem): Project {
    val (track, _) = findItem(item.id) ?: return this
    return replaceTrack(track.copy(items = track.items.map { if (it.id == item.id) item else it }))
}

private const val SCALE_TOLERANCE = 0.001f
private const val STEP_TOLERANCE = 0.001f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f

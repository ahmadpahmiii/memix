package app.memix.feature.videoeditor.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixTheme
import app.memix.core.model.project.TrackKind
import app.memix.feature.videoeditor.LandedItems
import kotlin.math.roundToLong

/**
 * The timeline's motion after an edit (spec P1-06 → Motion and haptics), read only in draw code:
 * - **Ripple:** after an edit moves main clips (trim, delete, duplicate, undo), they slide from their old places to
 *   the new ones in 200 ms; with reduce motion they're simply there.
 * - **Bonk:** items that land (a duplicate, a meme sound, added or replaced media) scale 1 → 1.12 → 1 over 240 ms, each
 *   around its own center; with reduce motion they fade in over 120 ms instead.
 * - **Lane slide:** when a lane comes or goes (a sound lands on a new lane, a lane empties), the rows below slide to
 *   their new places in 200 ms (spec P1-05 → Motion); with reduce motion they're simply there.
 */
@Stable
internal class EditMotion {
    /** The timeline the last ripple was worked out from. */
    var lastTimeline: TimelineUi? = null

    private var slideFromUs: Map<String, Long> = emptyMap()
    val slide = Animatable(1f)

    var landedItemIds by mutableStateOf<Set<String>>(emptySet())
        private set
    val bonkScale = Animatable(1f)
    val bonkAlpha = Animatable(1f)

    /** The lanes the last lane slide was worked out from. */
    var lastLanes: LaneLayout? = null

    private var laneShiftFromPx: Map<String, Float> = emptyMap()
    private val laneSlide = Animatable(1f)

    /** How far row [key] (a track id, or [ADD_SOUND_ROW_KEY]) is drawn from its place now, px, while lanes slide. */
    fun laneShiftPx(key: String): Float {
        val fromPx = laneShiftFromPx[key] ?: return 0f
        val progress = laneSlide.value
        return if (progress >= 1f) 0f else fromPx * (1f - progress)
    }

    suspend fun slideLanes(fromPx: Map<String, Float>) {
        laneShiftFromPx = fromPx
        laneSlide.snapTo(0f)
        laneSlide.animateTo(1f, tween(MemixMotion.durationSheet))
    }

    /** Where main clip [item] starts on screen now: on its way from its old place while the ripple runs. */
    fun startUsOf(item: ItemUi): Long {
        val fromUs = slideFromUs[item.id] ?: return item.startUs
        val progress = slide.value
        return if (progress >= 1f) item.startUs else fromUs + ((item.startUs - fromUs) * progress).roundToLong()
    }

    suspend fun ripple(fromUs: Map<String, Long>) {
        slideFromUs = fromUs
        slide.snapTo(0f)
        slide.animateTo(1f, tween(MemixMotion.durationSheet))
    }

    suspend fun bonk(itemIds: Set<String>, reduceMotion: Boolean) {
        landedItemIds = itemIds
        if (reduceMotion) {
            bonkAlpha.snapTo(0f)
            bonkAlpha.animateTo(1f, tween(MemixMotion.durationPress))
        } else {
            bonkScale.snapTo(1f)
            bonkScale.animateTo(
                1f,
                keyframes {
                    durationMillis = MemixMotion.durationBonk
                    BONK_PEAK at MemixMotion.durationBonk / 2
                },
            )
        }
        landedItemIds = emptySet()
    }

    companion object {
        /** The bonk's largest scale (spec P1-06 → Motion: 1 → 1.12 → 1). */
        private const val BONK_PEAK = 1.12f

        /** [laneShiftPx]'s key for the "Add a meme sound" row, which has no track. */
        const val ADD_SOUND_ROW_KEY = "add-sound-row"
    }
}

/**
 * Slides the rows that changed place when [lanes] gets a lane more or less (spec P1-05 → Motion: 200 ms, nothing with
 * reduce motion). A new lane itself just appears; its sound bonks.
 */
@Composable
internal fun AnimateLanes(motion: EditMotion, lanes: LaneLayout) {
    val reduceMotion = MemixTheme.reduceMotion
    LaunchedEffect(lanes) {
        val before = motion.lastLanes
        motion.lastLanes = lanes
        if (before == null || reduceMotion || before.rows.size == lanes.rows.size) return@LaunchedEffect
        val shifts = HashMap<String, Float>()
        for (row in lanes.rows) {
            val old = before.rows.firstOrNull { it.track.id == row.track.id } ?: continue
            if (old.topPx != row.topPx) shifts[row.track.id] = old.topPx - row.topPx
        }
        val oldAddRow = before.addSoundRow
        val newAddRow = lanes.addSoundRow
        if (oldAddRow != null && newAddRow != null && oldAddRow.topPx != newAddRow.topPx) {
            shifts[EditMotion.ADD_SOUND_ROW_KEY] = oldAddRow.topPx - newAddRow.topPx
        }
        if (shifts.isNotEmpty()) motion.slideLanes(shifts)
    }
}

/**
 * Runs [motion]'s ripple whenever a new timeline moves main clips that were there before, except while reorder
 * mode's tiles handle the change themselves, and its bonk when [landed] names new items (with the Confirm haptic for a
 * meme sound).
 */
@Composable
internal fun AnimateEdits(motion: EditMotion, timeline: TimelineUi, landed: LandedItems?, controller: TimelineController) {
    val reduceMotion = MemixTheme.reduceMotion
    LaunchedEffect(timeline) {
        val before = motion.lastTimeline
        motion.lastTimeline = timeline
        if (before == null || reduceMotion || controller.state.reorder != null) return@LaunchedEffect
        val moved = movedMainClips(before, timeline)
        if (moved.isNotEmpty()) motion.ripple(moved)
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(landed) {
        if (landed == null) return@LaunchedEffect
        // The tick when a sound lands (spec P1-06 → Motion and haptics; mobile-performance skill → Feel).
        if (landed.isSound) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        motion.bonk(landed.itemIds, reduceMotion)
    }
}

/** The main clips in both timelines whose start changed, by id, with their old start. */
private fun movedMainClips(before: TimelineUi, after: TimelineUi): Map<String, Long> {
    val oldStarts = before.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items?.associate { it.id to it.startUs } ?: return emptyMap()
    val newClips = after.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items ?: return emptyMap()
    return newClips.mapNotNull { clip -> oldStarts[clip.id]?.takeIf { it != clip.startUs }?.let { clip.id to it } }.toMap()
}

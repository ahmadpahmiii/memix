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
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixTheme
import app.memix.core.model.project.TrackKind
import app.memix.feature.videoeditor.LandedItem
import kotlin.math.roundToLong

/**
 * The timeline's motion after an edit (spec P1-06 → Motion and haptics), read only in draw code:
 * - **Ripple:** after an edit moves main clips (trim, delete, duplicate, undo), they slide from their old places to
 *   the new ones in 200 ms; with reduce motion they're simply there.
 * - **Bonk:** an item that lands (a duplicate; P1-08 meme sounds) scales 1 → 1.12 → 1 over 240 ms; with reduce motion
 *   it fades in over 120 ms instead.
 */
@Stable
internal class EditMotion {
    /** The timeline the last ripple was worked out from. */
    var lastTimeline: TimelineUi? = null

    private var slideFromUs: Map<String, Long> = emptyMap()
    val slide = Animatable(1f)

    var landedItemId by mutableStateOf<String?>(null)
        private set
    val bonkScale = Animatable(1f)
    val bonkAlpha = Animatable(1f)

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

    suspend fun bonk(itemId: String, reduceMotion: Boolean) {
        landedItemId = itemId
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
        landedItemId = null
    }

    private companion object {
        /** The bonk's largest scale (spec P1-06 → Motion: 1 → 1.12 → 1). */
        const val BONK_PEAK = 1.12f
    }
}

/**
 * Runs [motion]'s ripple whenever a new timeline moves main clips that were there before, except while reorder
 * mode's tiles handle the change themselves, and its bonk when [landed] names a new item.
 */
@Composable
internal fun AnimateEdits(motion: EditMotion, timeline: TimelineUi, landed: LandedItem?, controller: TimelineController) {
    val reduceMotion = MemixTheme.reduceMotion
    LaunchedEffect(timeline) {
        val before = motion.lastTimeline
        motion.lastTimeline = timeline
        if (before == null || reduceMotion || controller.state.reorder != null) return@LaunchedEffect
        val moved = movedMainClips(before, timeline)
        if (moved.isNotEmpty()) motion.ripple(moved)
    }
    LaunchedEffect(landed) {
        if (landed != null) motion.bonk(landed.itemId, reduceMotion)
    }
}

/** The main clips in both timelines whose start changed, by id, with their old start. */
private fun movedMainClips(before: TimelineUi, after: TimelineUi): Map<String, Long> {
    val oldStarts = before.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items?.associate { it.id to it.startUs } ?: return emptyMap()
    val newClips = after.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items ?: return emptyMap()
    return newClips.mapNotNull { clip -> oldStarts[clip.id]?.takeIf { it != clip.startUs }?.let { clip.id to it } }.toMap()
}

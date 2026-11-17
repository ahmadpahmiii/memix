package app.memix.core.domain.project

import app.memix.core.model.project.EffectCatalog
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind

/*
 * Effect edits (spec docs/ux/specs/P4-06-video-effects.md → The effect on the timeline, Rules). An effect is an item on an Effects lane that
 * keeps its own times like every item off the main track, so a main-clip ripple never moves it (owner, 8 Oct 2026). Move, trim, split,
 * duplicate and delete are the shared edits (TimelineEdits, MoveEdits); this file adds landing, Change effect and Intensity, and the
 * three-at-once rule, which the editor checks after any edit ([exceedsEffectOverlap]).
 */

/**
 * Where an effect of default length [defaultDurationUs] goes when placed at the playhead [atUs] in a video [videoLengthUs] long, as (start,
 * duration). At the playhead with its default length, unless less than half a second of video remains (or the playhead is at or past the end),
 * when it ends at the end instead with the smaller of its default length and the video.
 */
fun effectLandingSpan(atUs: Long, defaultDurationUs: Long, videoLengthUs: Long): Pair<Long, Long> {
    if (videoLengthUs <= 0) return atUs.coerceAtLeast(0) to defaultDurationUs
    val remainingUs = videoLengthUs - atUs
    if (remainingUs >= EffectCatalog.MIN_REMAINING_FOR_PLAYHEAD_LANDING_US) return atUs.coerceAtLeast(0) to defaultDurationUs
    val durationUs = minOf(defaultDurationUs, videoLengthUs)
    return (videoLengthUs - durationUs) to durationUs
}

/**
 * Adds effect [effectId] as [itemId] at the playhead [atUs] (see [effectLandingSpan]) with its default intensity, on the first Effects lane free
 * for its whole span, else a new lane [newTrackId] under the last. An id this build doesn't draw adds nothing.
 */
fun Project.addEffect(effectId: String, atUs: Long, itemId: String, newTrackId: String): Project {
    val def = EffectCatalog.of(effectId)?.takeIf { it.isBuilt } ?: return this
    val (startUs, durationUs) = effectLandingSpan(atUs, def.defaultDurationUs, videoLengthUs())
    val item = EffectItem(itemId, startUs, durationUs, def.id, def.defaultIntensity)
    return withItemOnFreeLane(item, TrackKind.EFFECT, tracks().filter { it.kind == TrackKind.EFFECT }, newTrackId)
}

/** Effect [itemId] as another effect: same place, length and intensity (spec → Fast path, "Change my mind"). */
fun Project.changeEffect(itemId: String, effectId: String): Project {
    if (EffectCatalog.of(effectId)?.isBuilt != true) return this
    val item = effectItem(itemId) ?: return this
    return if (item.effectId == effectId) this else replaceEffect(item.copy(effectId = effectId))
}

/** Effect [itemId]'s intensity, 0 (does nothing, stays on the timeline) to 1. */
fun Project.setEffectIntensity(itemId: String, intensity: Float): Project {
    val item = effectItem(itemId) ?: return this
    val clamped = intensity.coerceIn(0f, 1f)
    return if (item.intensity == clamped) this else replaceEffect(item.copy(intensity = clamped))
}

/** Effect item [itemId], or null when it isn't one. */
fun Project.effectItem(itemId: String): EffectItem? = findItem(itemId)?.item as? EffectItem

/** Every effect item in lane order (first lane first), then by start: the order they are applied in. */
fun Project.effectItemsInLaneOrder(): List<EffectItem> =
    tracks().filter { it.kind == TrackKind.EFFECT }.flatMap { track -> track.items.filterIsInstance<EffectItem>().sortedBy { it.startUs } }

/** True when more than [EffectCatalog.MAX_OVERLAP] effect items share any moment: the edit that made it is refused (spec → Stacking). */
fun Project.exceedsEffectOverlap(): Boolean {
    val items = effectItemsInLaneOrder().filter { it.durationUs > 0 }
    if (items.size <= EffectCatalog.MAX_OVERLAP) return false
    // An item ends where the next may start: ends sort before starts at the same time.
    val events = items.flatMap { listOf(it.startUs to 1, it.startUs + it.durationUs to -1) }.sortedWith(compareBy({ it.first }, { it.second }))
    var open = 0
    for ((_, change) in events) {
        open = (open + change).coerceAtLeast(0)
        if (open > EffectCatalog.MAX_OVERLAP) return true
    }
    return false
}

/**
 * True when more than [EffectCatalog.MAX_FLASHES_PER_SECOND] flash events fall within any one second (spec P4-06 → Photosensitivity; WCAG 2.3.1). The events are
 * the start of each Flash item and the flashes of transitions ([transitionFlashTimesUs]: one for each Dip to white, three for each Glitch cut), so the two
 * kinds count together.
 */
fun Project.exceedsFlashRate(): Boolean {
    val starts = (effectItemsInLaneOrder().filter { it.effectId == EffectCatalog.FLASH_ID }.map { it.startUs } + transitionFlashTimesUs()).sorted()
    val limit = EffectCatalog.MAX_FLASHES_PER_SECOND
    return (0..starts.size - limit - 1).any { starts[it + limit] - starts[it] < FLASH_WINDOW_US }
}

private const val FLASH_WINDOW_US = 1_000_000L

/** Why an edit of effect items is refused. */
enum class EffectRefusal { OVERLAP, FLASH_RATE }

/** The rule [after] breaks that [before] didn't, or null: the edit session turns such an edit into no step, so no editor path can skip the rules. */
fun effectRefusal(before: Project, after: Project): EffectRefusal? = when {
    after.exceedsEffectOverlap() && !before.exceedsEffectOverlap() -> EffectRefusal.OVERLAP
    after.exceedsFlashRate() && !before.exceedsFlashRate() -> EffectRefusal.FLASH_RATE
    else -> null
}

private fun Project.replaceEffect(item: EffectItem): Project {
    val (track, _) = findItem(item.id) ?: return this
    return replaceTrack(track.copy(items = track.items.map { if (it.id == item.id) item else it }))
}

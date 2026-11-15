package app.memix.core.domain.project

import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.AudioEffects
import app.memix.core.model.project.AudioEffectsCatalog
import app.memix.core.model.project.Project

/*
 * The Audio effects panel's edits (spec docs/ux/specs/P3-09-audio-effects.md). They act on a meme sound or an audio clip
 * and, like every edit, return a new Project, or this one unchanged when they can't apply (which the edit session records
 * as no step). The source file is never touched: the effects live in the clip. P3-09b adds its setters here.
 */

/** The effects of sound [itemId]; null when it isn't a meme sound or an audio clip. */
fun Project.audioEffectsOf(itemId: String): AudioEffects? = (findItem(itemId)?.item as? AudioClip)?.effects

/** The lane (track id) item [itemId] is on now, so an edit that may move it to another lane can tell whether it did. */
fun Project.laneOf(itemId: String): String? = findItem(itemId)?.track?.id

/** True when sound [itemId] is long enough to fade: shorter than [AudioEffectsCatalog.MIN_FADEABLE_US] it keeps both fades at 0. */
fun Project.canFade(itemId: String): Boolean = (findItem(itemId)?.item as? AudioClip)?.let { it.durationUs >= AudioEffectsCatalog.MIN_FADEABLE_US } == true

/**
 * Fades sound [itemId] in over [fadeUs] of its start. Kept between 0 and [AudioEffectsCatalog.MAX_FADE_US], and never so long
 * that the two fades add up to more than the clip: it stops at what Fade out leaves, and doesn't push it.
 */
fun Project.setFadeIn(itemId: String, fadeUs: Long): Project = withClip(itemId) { clip ->
    if (!canFade(itemId)) return@withClip clip
    val room = (clip.durationUs - clip.effects.fadeOutUs).coerceAtMost(AudioEffectsCatalog.MAX_FADE_US)
    clip.copy(effects = clip.effects.copy(fadeInUs = fadeUs.coerceIn(0, room.coerceAtLeast(0))))
}

/** Fades sound [itemId] out over [fadeUs] of its end; the same limits as [setFadeIn]. */
fun Project.setFadeOut(itemId: String, fadeUs: Long): Project = withClip(itemId) { clip ->
    if (!canFade(itemId)) return@withClip clip
    val room = (clip.durationUs - clip.effects.fadeInUs).coerceAtMost(AudioEffectsCatalog.MAX_FADE_US)
    clip.copy(effects = clip.effects.copy(fadeOutUs = fadeUs.coerceIn(0, room.coerceAtLeast(0))))
}

/** Shifts the pitch of sound [itemId] by [semitones] (-12 to +12) without changing its speed. */
fun Project.setPitch(itemId: String, semitones: Int): Project = withClip(itemId) { clip ->
    clip.copy(effects = clip.effects.copy(pitchSemitones = semitones.coerceIn(AudioEffectsCatalog.PITCH_RANGE_SEMITONES)))
}

/**
 * Plays sound [itemId] at [speed] (0.5 to 2) without changing its pitch. The clip's length on the timeline changes with
 * it: its start stays and its end moves; the fades are shortened if they no longer fit. A clip that would now overlap the
 * next one on its lane moves like a dragged one does ([moveItem]): to the first lane of its kind with room, else to a new
 * lane [newTrackId]; it never overwrites or shortens a neighbor (spec P3-09 rule 4). Length and lane are one step.
 */
fun Project.setSpeed(itemId: String, speed: Float, newTrackId: String): Project =
    withEffects(itemId, newTrackId) { it.copy(speed = AudioEffectsCatalog.cleanSpeed(speed)) }

/**
 * Sets every effect of sound [itemId] back to its default, the whole clip (owner, 11 Nov 2026), in one step. Back to
 * speed 1 can make the clip longer, so the lane rule of [setSpeed] applies.
 */
fun Project.resetAudioEffects(itemId: String, newTrackId: String): Project = withEffects(itemId, newTrackId) { AudioEffects() }

// The clip with [change] applied to its effects, its fades fitted to its new length, and moved to a lane with room if it grew into a neighbor.
private fun Project.withEffects(itemId: String, newTrackId: String, change: (AudioEffects) -> AudioEffects): Project {
    val (track, item) = findItem(itemId) ?: return this
    val clip = item as? AudioClip ?: return this
    val changed = change(clip.effects)
    if (changed == clip.effects) return this
    val resized = clip.copy(effects = changed)
    val fitted = resized.copy(effects = changed.withFadesFitted(resized.durationUs))
    val edited = replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) fitted else it }))
    return edited.moveItem(itemId, fitted.startUs, LaneTarget.Lane(track.id), newTrackId)
}

private fun Project.withClip(itemId: String, change: (AudioClip) -> AudioClip): Project {
    val (track, item) = findItem(itemId) ?: return this
    val clip = item as? AudioClip ?: return this
    val changed = change(clip)
    if (changed == clip) return this
    return replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) changed else it }))
}

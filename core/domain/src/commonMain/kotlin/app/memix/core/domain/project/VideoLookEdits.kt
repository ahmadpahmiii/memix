package app.memix.core.domain.project

import app.memix.core.domain.EditorTool
import app.memix.core.model.project.ClipLook
import app.memix.core.model.project.ImageAdjust
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.look
import app.memix.core.model.project.withLook

/*
 * Video filter and adjustment edits (P4-07; spec docs/ux/specs/P4-07-video-filters-addendum.md → The tool and its scope). Pure, like every
 * edit; the editor records each returned project as one undo step. The look is the photo editor's [ImageAdjust] and filter, kept in
 * [MediaClip.edit]. Main clips and overlays, video and photo clips alike, can have one; captions, stickers and sounds can't.
 */

/** The look of clip [itemId] (a main clip or an overlay), or null when it isn't one. */
fun Project.clipLookOf(itemId: String): ClipLook? = keyableClip(itemId)?.look

/** Every main clip, in play order (the scope of Filters opened from the main bar). */
fun Project.mainLookClips(): List<MediaClip> = mainTrack()?.items.orEmpty().filterIsInstance<MediaClip>()

/** The look every main clip shares, or null when they differ or there are none (the panel's "mixed" state). */
fun Project.sharedMainLook(): ClipLook? = mainLookClips().map { it.look }.distinct().singleOrNull()

/** True when main clips have more than one look, so the panel shows no selected tile and writes one setting to all. */
fun Project.mainLooksMixed(): Boolean = mainLookClips().map { it.look }.distinct().size > 1

/**
 * Sets clip [itemId]'s look to [change] of its current one. A clip that isn't a main clip or an overlay is left alone. Returns this
 * project unchanged when the look doesn't change, so no undo step is recorded.
 */
fun Project.updateClipLook(itemId: String, change: (ClipLook) -> ClipLook): Project {
    val clip = keyableClip(itemId) ?: return this
    val updated = clip.withLook(change(clip.look))
    if (updated == clip) return this
    val (track, _) = findItem(itemId) ?: return this
    return replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) updated else it }))
}

/**
 * Applies [change] to the look of every main clip in one step: the main bar's Filters. [change] gets each clip's own look, so a setting it
 * doesn't touch stays per clip. Returns this project unchanged when no look changes.
 */
fun Project.updateMainLooks(change: (ClipLook) -> ClipLook): Project {
    val track = mainTrack() ?: return this
    val items = track.items.map { item -> if (item is MediaClip) item.withLook(change(item.look)) else item }
    return if (items == track.items) this else replaceTrack(track.copy(items = items))
}

/** The look a main clip added now takes: the one every main clip shares, none when they differ or there are none (spec → The tool and its scope). */
fun Project.lookForNewMainClip(): ClipLook = sharedMainLook() ?: ClipLook.NONE

/** [this] look with [tab]'s part put back to normal: no filter, or all six adjustments 0. Deep-fry is a photo tool and changes nothing here. */
fun ClipLook.resetTab(tab: EditPhotoTab): ClipLook = when (tab) {
    EditPhotoTab.FILTERS -> copy(filter = null)
    EditPhotoTab.ADJUST -> copy(adjust = ImageAdjust())
    EditPhotoTab.DEEP_FRY -> this
}

/**
 * The `tool_use` events for a step that took the project from [before] to [after]: `filter` once if any clip's filter changed, and each
 * `adjust_*` that changed on any clip (spec → Analytics: the photo editor's ids, no scope parameter). Empty when no look changed.
 */
fun changedLookTools(before: Project, after: Project): List<EditorTool> {
    val pairs = before.looksById().mapNotNull { (id, old) -> after.looksById()[id]?.let { old to it } }
    return buildList {
        if (pairs.any { (old, new) -> old.filter != new.filter }) add(EditorTool.FILTER)
        for (part in AdjustPart.entries) {
            if (pairs.any { (old, new) -> old.adjust.valueOf(part) != new.adjust.valueOf(part) }) add(part.tool)
        }
    }
}

private fun Project.looksById(): Map<String, ClipLook> =
    tracks().filter { it.kind == TrackKind.MAIN_VIDEO || it.kind == TrackKind.OVERLAY }
        .flatMap { it.items }.filterIsInstance<MediaClip>().associate { it.id to it.look }

package app.memix.core.domain.project

import app.memix.core.model.project.ChromaKey
import app.memix.core.model.project.ChromaKeyCatalog
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind

/**
 * Green screen edits (P4-04). Pure; the editor records each returned project as one undo step. Keying applies to main clips and
 * overlays, video and photo alike (spec → Entry and exit); everything else has no picture to key.
 */

/** The keyable clip [itemId] (a main clip or an overlay), or null. */
fun Project.keyableClip(itemId: String): MediaClip? {
    val (track, item) = findItem(itemId) ?: return null
    return (item as? MediaClip)?.takeIf { track.kind == TrackKind.MAIN_VIDEO || track.kind == TrackKind.OVERLAY }
}

/** The key of clip [itemId], or null when keying is off or the item isn't a keyable clip. */
fun Project.chromaKeyOf(itemId: String): ChromaKey? = keyableClip(itemId)?.chromaKey

/**
 * Sets clip [itemId]'s green screen to [key] (null turns it off). The color is made opaque and strength and softness are held to
 * 0..1. Returns this project unchanged when nothing changes, so no undo step is recorded.
 */
fun Project.setClipChromaKey(itemId: String, key: ChromaKey?): Project {
    val clip = keyableClip(itemId) ?: return this
    val clean = key?.let { ChromaKeyCatalog.keyOf(it.colorArgb, it.strength, it.softness) }
    if (clip.chromaKey == clean) return this
    val (track, _) = findItem(itemId) ?: return this
    return replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) clip.copy(chromaKey = clean) else it }))
}

/** Keying turns on at the defaults when the panel opens on an unkeyed clip; a keyed clip keeps its values (spec → Entry and exit). */
fun Project.keyedAtDefaults(itemId: String): Project =
    if (chromaKeyOf(itemId) == null) setClipChromaKey(itemId, ChromaKeyCatalog.DEFAULT) else this

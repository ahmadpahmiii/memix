package app.memix.core.data.project

import kotlinx.serialization.json.JsonObject

/**
 * One step of the migration chain: takes project JSON saved at [fromVersion] and returns it in the
 * shape of version [fromVersion] + 1. [ProjectJsonMigrator] writes the new schemaVersion itself.
 */
internal class ProjectMigration(val fromVersion: Int, val migrate: (JsonObject) -> JsonObject)

/**
 * Every change to the saved shape of Project adds one step here and bumps Project.CURRENT_SCHEMA_VERSION
 * (CLAUDE.md rule 6); then open a draft saved before the change to check it. For example, renaming
 * Project.name to title in version 2 would add:
 *
 *     ProjectMigration(fromVersion = 1) { json -> JsonObject(json - "name" + ("title" to json.getValue("name"))) }
 */
internal val projectMigrations: List<ProjectMigration> = listOf(
    // 1 → 2 (P1-02): MediaRef gained durationUs, pixelSize and hasAudio, measured at import. All three are optional
    // with null defaults, so version-1 JSON decodes as it is and the step only moves the version on. Media in a
    // version-1 draft keeps null for them; the editor reads them from the copy when it opens such a draft.
    ProjectMigration(fromVersion = 1) { json -> json },
    // 2 → 3 (P2-01): Layer gained locked, hidden and opacity. Each has a default (unlocked, shown, 1.0) and the
    // codec writes defaults, so version-2 JSON decodes as it is: a layer saved before this step looks the same.
    ProjectMigration(fromVersion = 2) { json -> json },
    // 3 → 4 (P2-12): CanvasBackground gained the "image" subtype. A new subtype leaves every saved "color" and "blur"
    // background as it was, so version-3 JSON decodes as it is and the step only moves the version on.
    ProjectMigration(fromVersion = 3) { json -> json },
    // 4 → 5 (P2-02): PhotoScene gained the nullable "format" (null is plain) and Layer the "bubble_layer" subtype. Version-4 JSON
    // has no "format", which decodes as null, and no bubble, so it decodes as it is and opens plain, looking as saved.
    ProjectMigration(fromVersion = 4) { json -> json },
    // 5 → 6 (P2-11): CaptionStyle gained the nullable "shadow" and "box" (null is off). CaptionStyle is shared, so video and
    // photo drafts both pass here; version-5 JSON has neither field, which decodes as null, so a caption saved before looks
    // as saved and the step only moves the version on.
    ProjectMigration(fromVersion = 5) { json -> json },
    // 6 → 7 (P2-04): ImageLayer gained the nullable "edit" (crop, turn, flip, straighten, adjustments, filter, deep-fry). Version-6
    // JSON has none, which decodes as null (a layer with no edit), so a picture saved before looks as saved.
    ProjectMigration(fromVersion = 6) { json -> json },
    // 7 → 8 (P2-05a): the new layer kind "drawing_layer". A draft saved before has none, so its JSON decodes as it is and the step
    // only moves the version on. The censor half (P2-05b) is committed later and bumps to 9 the same way, so each version names one shape.
    ProjectMigration(fromVersion = 7) { json -> json },
    // 8 → 9 (P2-05b): the new layer kind "censor_layer". A draft saved before has none, so its JSON decodes as it is.
    ProjectMigration(fromVersion = 8) { json -> json },
    // 9 → 10 (P2-07): ImageLayer gained the nullable "cutOut" (mask file, refine strokes, softness). Version-9 JSON has none, which
    // decodes as null (the whole picture), so a draft saved before opens as saved and the step only moves the version on.
    ProjectMigration(fromVersion = 9) { json -> json },
    // 10 → 11 (P3-07): MediaOrigin gained the "my_sound" subtype (a sound from My sounds, with the name it had when it was added). A new
    // subtype leaves every saved origin as it was, so version-10 JSON decodes as it is and the step only moves the version on.
    ProjectMigration(fromVersion = 10) { json -> json },
    // 11 → 12 (P3-08): MediaOrigin gained the "recording" subtype (a voiceover take). Again a new subtype only, so version-11 JSON decodes as it is.
    ProjectMigration(fromVersion = 11) { json -> json },
    // 12 → 13 (P3-09a): AudioClip gained "effects" (fades, pitch, speed and the P3-09b voice, bass, distortion and echo fields), all with
    // defaults that mean off. Version-12 JSON has no "effects", which decodes as the defaults: a sound saved before plays as saved, at
    // speed 1 so its length is unchanged. The step only moves the version on.
    ProjectMigration(fromVersion = 12) { json -> json },
    // 13 → 14 (P4-01a): MediaClip gained "transform" (center, scale, turn), "opacity" (overlay tracks) and "edit" (P4-16's crop, turns and mirrors, inert until then). All have
    // defaults (centered, scale 1, 0°, fully opaque, no edit) and the codec writes defaults, so version-13 JSON decodes as it is; a main clip
    // ignores both, and an overlay track saved before (only the debug hand-check projects had one) draws as a full-canvas Fit overlay.
    ProjectMigration(fromVersion = 13) { json -> json },
    // 14 → 15 (P4-02a): MediaClip, AudioClip, TextItem and StickerItem gained "keyframes" (a list, empty by default). Version-14 JSON has no
    // "keyframes", which decodes as none: an item saved before moves as it did. The step only moves the version on.
    ProjectMigration(fromVersion = 14) { json -> json },
    // 15 → 16 (P4-04): MediaClip gained the nullable "chromaKey" (color, strength, softness; null is off). Version-15 JSON has none, which
    // decodes as null: a clip saved before is unkeyed and looks as saved. The step only moves the version on.
    ProjectMigration(fromVersion = 15) { json -> json },
    // 16 → 17 (P4-03b): MediaClip gained the nullable "mask" (shape, center, size, turn, softness, invert, corner roundness; null is none). Version-16
    // JSON has none, which decodes as null: a clip saved before is unmasked and looks as saved. The step only moves the version on.
    ProjectMigration(fromVersion = 16) { json -> json },
)

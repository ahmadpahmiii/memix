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
)

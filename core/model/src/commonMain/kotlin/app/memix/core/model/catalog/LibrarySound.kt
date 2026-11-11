package app.memix.core.model.catalog

/**
 * A sound as the online catalog lists it (P3-01): everything the Sounds screen, the details sheet and the picker show.
 * [CatalogSound] is the same sound reduced to what the editor needs.
 */
data class LibrarySound(
    /** Permanent catalog id; projects refer to the sound by it (`MediaOrigin.CatalogItem`). */
    val id: String,
    val title: LocalizedText,
    val categoryId: String,
    /** Storage key of the audio (`sounds/2026/11/<id>.m4a`); the address is built with `mediaUrl`, never stored. */
    val filePath: String,
    /** How long the whole sound plays, in microseconds. */
    val durationUs: Long,
    /** Storage key of the waveform file, or null: the sound then shows the plain rail. */
    val waveformPath: String?,
    /** `CC0`, `CC-BY`, `royalty-free`, `own` or `licensed`, as the license record has it. */
    val licenseType: String,
    val sourceUrl: String,
    /** The attribution the license asks for; null when it asks for none. */
    val credit: String?,
) {
    /** What the meta line credits: the attribution, else the license type. Never translated. */
    val creditLine: String get() = credit ?: licenseType

    fun toCatalogSound(): CatalogSound = CatalogSound(id = id, title = title, durationUs = durationUs, credit = creditLine)
}

package app.memix.core.model.catalog

/** A sound category chip. The catalog names it in every app language, in the order of [sort]. */
data class SoundCategory(
    val id: String,
    val name: LocalizedText,
    val sort: Int,
)

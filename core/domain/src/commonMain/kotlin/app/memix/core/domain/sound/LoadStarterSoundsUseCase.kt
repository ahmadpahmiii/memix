package app.memix.core.domain.sound

import app.memix.core.model.catalog.CatalogSound

/** The bundled starter pack, in its curated order (spec P1-06 → Meme sounds sheet). */
class LoadStarterSoundsUseCase(private val library: SoundLibrary) {
    suspend operator fun invoke(): List<CatalogSound> = library.starterSounds()
}

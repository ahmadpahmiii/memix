package app.memix.core.domain.sound

/**
 * The ids of the sounds whose audio is on the phone, which is what the offline search covers (spec P3-02 → Search).
 * Today that is the bundled starter pack; P3-04 adds the downloaded sounds and P3-07 the user's own.
 */
class GetSavedSoundIdsUseCase(private val library: SoundLibrary) {
    suspend operator fun invoke(): Set<String> = library.starterSounds().mapTo(HashSet()) { it.id }
}

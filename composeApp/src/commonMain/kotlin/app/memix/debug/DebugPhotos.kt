package app.memix.debug

/**
 * Writes the test pictures the photo editor's debug scenes and photo-shape check use into the app's files, under
 * `debug-media/photo-scenes/` (see [PhotoCheckProjects]), once: pictures already there are kept. Generated rather than
 * pushed by hand, so the 12 MP-class scene needs nothing from the tester. Returns false where it can't (iOS).
 */
internal expect suspend fun writeDebugPhotos(): Boolean

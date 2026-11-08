package app.memix.engine.photo

import app.memix.core.domain.photo.PhotoRenderer
import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.SavedPictureSource
import app.memix.core.ui.scene.SceneImageSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val photoEngineModule = module {
    single<ImageToolRenderer> { AndroidImageToolRenderer(Dispatchers.Default, logger = get()) }
    single<SceneImageSource> { AndroidSceneImageSource(androidContext().filesDir, Dispatchers.IO, logger = get()) }
    // One scene renderer for the export and the debug render hook, so the file equals the hook's PNG (P2-08).
    single {
        val filesDir = androidContext().filesDir
        PhotoSceneRenderer(get(), get(), get(), get(), isFilePresent = { path -> File(filesDir, path).exists() }, drawDispatcher = Dispatchers.Default)
    }
    single<PhotoRenderer> { AndroidPhotoRenderer(androidContext(), get(), Dispatchers.IO, get()) }
    single<SavedPictureSource> { AndroidSavedPictureSource(androidContext(), Dispatchers.IO, get()) }
}

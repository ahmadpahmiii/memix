package app.memix.engine.photo

import app.memix.core.ui.scene.SceneImageSource
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val photoEngineModule = module {
    single<SceneImageSource> { AndroidSceneImageSource(androidContext().filesDir, Dispatchers.IO, logger = get()) }
}

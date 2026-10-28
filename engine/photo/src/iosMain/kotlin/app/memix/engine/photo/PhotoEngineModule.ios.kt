package app.memix.engine.photo

import app.memix.core.ui.scene.SceneBitmap
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse
import org.koin.dsl.module

actual val photoEngineModule = module {
    single<SceneImageSource> { IosSceneImageSource }
}

// TODO(P7-09): decode with ImageIO at the target size (orientation applied), the same rules as Android's AndroidSceneImageSource.
private object IosSceneImageSource : SceneImageSource {
    override suspend fun decode(path: String, maxLongSidePx: Int, use: SceneImageUse): SceneBitmap? = null
}

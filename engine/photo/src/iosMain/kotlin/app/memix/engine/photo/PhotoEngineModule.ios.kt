package app.memix.engine.photo

import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.SceneBitmap
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse
import app.memix.core.ui.scene.UnsupportedImageToolRenderer
import org.koin.dsl.module

actual val photoEngineModule = module {
    single<SceneImageSource> { IosSceneImageSource }
    // TODO(P7-09): run the same ImagePipeline on CGImage pixels (or a Core Image kernel built from the same LUTs); until then the color tools report unsupported.
    single<ImageToolRenderer> { UnsupportedImageToolRenderer }
}

// TODO(P7-09): decode with ImageIO at the target size (orientation applied), the same rules as Android's AndroidSceneImageSource.
private object IosSceneImageSource : SceneImageSource {
    override suspend fun decode(path: String, maxLongSidePx: Int, use: SceneImageUse): SceneBitmap? = null
}

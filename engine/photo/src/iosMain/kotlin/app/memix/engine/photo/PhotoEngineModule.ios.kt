package app.memix.engine.photo

import app.memix.core.domain.photo.PhotoRenderOutcome
import app.memix.core.domain.photo.PhotoRenderRequest
import app.memix.core.domain.photo.PhotoRenderer
import app.memix.core.domain.video.ExportError
import app.memix.core.model.project.Project
import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.SceneBitmap
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse
import app.memix.core.ui.scene.UnsupportedImageToolRenderer
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

actual val photoEngineModule = module {
    single<SceneImageSource> { IosSceneImageSource }
    // TODO(P7-09): run the same ImagePipeline on CGImage pixels (or a Core Image kernel built from the same LUTs); until then the color tools report unsupported.
    single<ImageToolRenderer> { UnsupportedImageToolRenderer }
    // TODO(P7-09): file existence through NSFileManager, once the decoder below exists.
    single { PhotoSceneRenderer(get(), get(), get(), get(), isFilePresent = { false }, drawDispatcher = Dispatchers.Default) }
    single<PhotoRenderer> { IosPhotoRenderer }
}

// TODO(P7-09): decode with ImageIO at the target size (orientation applied), the same rules as Android's AndroidSceneImageSource.
private object IosSceneImageSource : SceneImageSource {
    override suspend fun decode(path: String, maxLongSidePx: Int, use: SceneImageUse): SceneBitmap? = null
}

// TODO(P7-09): PhotoSceneRenderer plus UIImage JPEG/PNG encoders and PHPhotoLibrary (spec P2-08 → Requests 1); until then nothing exports on iOS.
private object IosPhotoRenderer : PhotoRenderer {
    override suspend fun render(project: Project, request: PhotoRenderRequest): PhotoRenderOutcome = PhotoRenderOutcome.Failed(ExportError.ENCODER)

    override suspend fun deleteOldCopies() = Unit
}

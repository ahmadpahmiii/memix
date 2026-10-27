package app.memix.core.domain.video

import app.memix.core.model.project.Project

/** Starts the editor's live preview of a video project (see [PreviewSession]). The caller closes it. */
class StartPreviewUseCase(private val videoEngine: VideoEngine) {
    operator fun invoke(project: Project): PreviewSession = videoEngine.createPreview(project)
}

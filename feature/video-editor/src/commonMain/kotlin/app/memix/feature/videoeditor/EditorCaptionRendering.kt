package app.memix.feature.videoeditor

import app.memix.core.domain.text.MeasureCaptionUseCase
import app.memix.core.domain.text.PrepareCaptionsUseCase

/** The caption use cases the video editor needs (P1-10): the renderer's fonts and its layout, handed over together. */
class EditorCaptionRendering(val prepare: PrepareCaptionsUseCase, val measure: MeasureCaptionUseCase)

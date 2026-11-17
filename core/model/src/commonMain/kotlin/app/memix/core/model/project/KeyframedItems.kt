package app.memix.core.model.project

/*
 * What every kind of item says about keyframes (P4-02). Four kinds carry a `keyframes` list, but TimelineItem is sealed over five, so
 * the shared questions are answered here once instead of in every `when` that needs them.
 */

/** The item's keyframes in time order; empty for an item with none and for kinds that can't have any. */
fun TimelineItem.keyframeList(): List<Keyframe> = when (this) {
    is MediaClip -> keyframes
    is AudioClip -> keyframes
    is TextItem -> keyframes
    is StickerItem -> keyframes
    is EffectItem -> emptyList()
}

/** This item with [keyframes] in place of its own; an effect, which has no motion, is returned unchanged. */
fun TimelineItem.withKeyframes(keyframes: List<Keyframe>): TimelineItem = when (this) {
    is MediaClip -> copy(keyframes = keyframes)
    is AudioClip -> copy(keyframes = keyframes)
    is TextItem -> copy(keyframes = keyframes)
    is StickerItem -> copy(keyframes = keyframes)
    is EffectItem -> this
}

/** Whether the item can hold keyframes: everything but an effect. */
fun TimelineItem.canHaveKeyframes(): Boolean = this !is EffectItem

/**
 * The look an item has with no keyframes: its own transform, opacity and volume. [onOverlay] is whether a media clip floats over the
 * main video (only then it has an opacity); a main clip's opacity reads as 1.
 */
fun TimelineItem.staticLook(onOverlay: Boolean): ItemLook = when (this) {
    is MediaClip -> ItemLook(transform.centerX, transform.centerY, transform.scale, transform.rotationDegrees, if (onOverlay) opacity else 1f, volume)
    is AudioClip -> ItemLook(volume = volume)
    is TextItem -> transform.toLook()
    is StickerItem -> transform.toLook()
    is EffectItem -> ItemLook()
}

private fun Transform.toLook() = ItemLook(centerX, centerY, scale, rotationDegrees)

/**
 * The biggest volume the item reaches over its length: its own volume, or the loudest keyframe when it has keyframes. 0 means silent
 * the whole time, which the export leaves out.
 */
fun TimelineItem.peakVolume(): Float = when (this) {
    is MediaClip -> if (keyframes.isEmpty()) volume else keyframes.maxOf { it.look.volume }
    is AudioClip -> if (keyframes.isEmpty()) volume else keyframes.maxOf { it.look.volume }
    is TextItem, is StickerItem, is EffectItem -> 0f
}

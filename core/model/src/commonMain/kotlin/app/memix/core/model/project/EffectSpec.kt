package app.memix.core.model.project

import kotlinx.serialization.Serializable

/**
 * A visual effect as data: each engine (Media3, AVFoundation) renders the same spec, which keeps
 * Android and iOS output alike (CLAUDE.md rule 5). Filters and transitions use the same shape with
 * their own id prefix.
 */
@Serializable
data class EffectSpec(
    /** Stable, namespaced id such as "effect.zoom_punch". Never reuse an id for a different look. */
    val id: String,
    /** Numeric parameters by name; their ranges and defaults live in the effect registry. */
    val params: Map<String, Float> = emptyMap(),
)

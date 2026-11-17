package app.memix.engine.video

import app.memix.core.domain.project.effectItemsInLaneOrder
import app.memix.core.model.project.EffectCatalog
import app.memix.core.model.project.EffectEnvelope
import app.memix.core.model.project.Project

/**
 * One effect item as the engines draw it (P4-06): which effect, when, how strong, with its envelope resolved once. Times are project µs, which
 * is composition time (the main track starts at 0).
 */
internal data class EffectSpan(
    val itemId: String,
    val effectId: String,
    val envelope: EffectEnvelope,
    val startUs: Long,
    val durationUs: Long,
    val intensity: Float,
)

/**
 * The effect items of [project] this engine draws, in lane order (first lane first, the order they are applied in; spec P4-06 → Stacking).
 * Items of an id the catalog marks unbuilt are left out; an item at 0% stays, so a slider dragged up from 0 needs no new composition.
 */
internal fun effectSpansOf(project: Project): List<EffectSpan> = project.effectItemsInLaneOrder().mapNotNull { item ->
    val def = EffectCatalog.of(item.effectId)?.takeIf { it.isBuilt } ?: return@mapNotNull null
    EffectSpan(item.id, def.id, def.envelope, item.startUs, item.durationUs, item.intensity)
}

/** The ids of effect items this build doesn't draw yet (P4-06b's), for the plan's "not rendered" log lines. */
internal fun unbuiltEffectIdsOf(project: Project): List<String> =
    project.effectItemsInLaneOrder().filter { !EffectCatalog.isBuilt(it.effectId) }.map { it.effectId }.distinct()

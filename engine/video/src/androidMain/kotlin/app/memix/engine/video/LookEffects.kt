package app.memix.engine.video

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi

/**
 * The shader passes of a clip's look (P4-07), in order: color, then detail. Only the passes [live] was built with are in the list, so a
 * clip with no look has none and costs nothing. Reads the filter's table, so it runs on the composition builder's thread.
 */
@OptIn(UnstableApi::class)
internal fun lookEffectsOf(live: LiveLook?, tables: LutTables): List<Effect> {
    if (live == null) return emptyList()
    return buildList {
        if (LookPass.COLOR in live.passes) add(LookColorEffect(live, live.filterId?.let(tables::tableOf)))
        if (LookPass.DETAIL in live.passes) add(LookDetailEffect(live))
    }
}

package app.memix.core.data.catalog

import kotlin.time.Duration.Companion.hours

/** How old the phone's copy may be before opening a screen asks the network again (TECHNICAL_DESIGN → app-call table). */
internal object CatalogRefreshRules {
    val categories = 24.hours
    val soundPage = 6.hours
    val trending = 6.hours
    val removedCheck = 24.hours
}

package app.memix.core.domain.region

import app.memix.core.model.AppLanguage

/** What the Region sheet confirms: the app language and the country whose local sounds the user wants (ISO 3166-1 alpha-2). */
data class RegionChoice(val language: AppLanguage, val regionCode: String)

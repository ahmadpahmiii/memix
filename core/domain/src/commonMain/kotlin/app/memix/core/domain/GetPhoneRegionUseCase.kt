package app.memix.core.domain

import app.memix.core.model.Region

class GetPhoneRegionUseCase(private val deviceRegion: DeviceRegion) {
    operator fun invoke(): Region {
        val code = deviceRegion.regionCode() ?: return Region.Global
        return Region.Country(code, deviceRegion.displayName(code))
    }
}

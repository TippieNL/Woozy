package app.woozy.data.providers

import app.woozy.domain.model.LocationFix
import app.woozy.domain.model.SpeedLimit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the posted speed limit for the user's current road.
 *
 * Real implementations (Phase 3/4): OSM `maxspeed` via the routing engine's edge attributes
 * (Valhalla trace_attributes / route annotations) or a backend road database. Road-type defaults
 * are deliberately NOT used: a wrong limit is worse than "--".
 */
interface SpeedLimitProvider {
    fun speedLimits(fixes: Flow<LocationFix>): Flow<SpeedLimit>
}

/** Honest default until a data source is wired: always [SpeedLimit.Unknown]. */
@Singleton
class NoSpeedLimitDataProvider @Inject constructor() : SpeedLimitProvider {
    override fun speedLimits(fixes: Flow<LocationFix>): Flow<SpeedLimit> = fixes.map { SpeedLimit.Unknown }
}

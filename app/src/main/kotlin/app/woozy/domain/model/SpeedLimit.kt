package app.woozy.domain.model

/** Speed limit for the road the user is on. Never guessed: [Unknown] when no reliable data. */
sealed interface SpeedLimit {
    data object Unknown : SpeedLimit

    /** @param kmh limit as posted, in km/h (OSM maxspeed is normalised to km/h). */
    data class Known(val kmh: Int, val source: String) : SpeedLimit
}

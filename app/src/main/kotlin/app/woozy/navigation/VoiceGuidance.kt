package app.woozy.navigation

import app.woozy.domain.model.ManeuverKind
import app.woozy.domain.model.Route
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Decides when to speak turn instructions. Per maneuver at most:
 *  - FAR:  "In 600 metres, take the exit…" (time-based distance, scaled with speed)
 *  - NEAR: Valhalla's pre-transition instruction, ~7 s before the maneuver
 *  - POST: "Continue for 12 kilometres." right after a maneuver when a long stretch follows
 * Roundabout exits are never announced (the enter instruction already says which exit), and when
 * an instruction already ends with "Then <next maneuver>", the next maneuver's early alert is
 * dropped. Pure logic: returns the phrase to speak (or null); the caller does the speaking.
 */
class VoiceGuidance(private val imperial: () -> Boolean = { false }) {

    private enum class Stage { FAR, NEAR, POST }

    private val spoken = HashSet<Pair<Int, Stage>>()

    fun reset() = spoken.clear()

    /** Phrase for the start of guidance (the depart maneuver, often with "Then …"). */
    fun start(route: Route, maneuverStart: DoubleArray, speedMps: Double = 0.0): String? {
        val first = route.maneuvers.firstOrNull() ?: return null
        spoken += 0 to Stage.NEAR
        spoken += 0 to Stage.FAR
        val text = first.verbalPre ?: first.instruction
        coverFollowUp(0, text, maneuverStart, nearMeters(speedMps))
        return text
    }

    fun next(route: Route, maneuverStart: DoubleArray, progress: RouteProgress, speedMps: Double): String? {
        val i = progress.nextManeuverIndex ?: return null
        val maneuver = route.maneuvers[i]
        val d = progress.distanceToNextManeuver
        val speed = speedMps.coerceAtLeast(8.0) // assume ≥ ~30 km/h so thresholds don't collapse in traffic
        val near = nearMeters(speedMps)
        val far = (speed * FAR_SECONDS).coerceIn(300.0, 1_500.0)
        val silent = maneuver.kind == ManeuverKind.ROUNDABOUT_EXIT

        if (d <= near) {
            if (!spoken.add(i to Stage.NEAR)) return null
            spoken += i to Stage.FAR
            if (silent) return null
            val text = maneuver.verbalPre ?: maneuver.instruction
            coverFollowUp(i, text, maneuverStart, near)
            return text
        }
        if (silent) return null
        if (d <= far && d > near + 100 && spoken.add(i to Stage.FAR)) {
            val text = maneuver.verbalAlert ?: maneuver.verbalPre ?: maneuver.instruction
            return "In ${spokenDistance(d)}, ${text.lowerFirst()}"
        }
        // Just passed maneuver i-1 and a long stretch follows: confirm with "Continue for …".
        val previous = i - 1
        if (previous >= 0 && d > far + 500 && progress.distanceAlong - maneuverStart[previous] < 250) {
            val post = route.maneuvers[previous].verbalPost
            if (post != null && spoken.add(previous to Stage.POST)) return post
        }
        return null
    }

    private fun nearMeters(speedMps: Double) = (speedMps.coerceAtLeast(8.0) * NEAR_SECONDS).coerceAtLeast(40.0)

    /** "…Then turn left" already told the driver about maneuver i+1: skip its early alert, and its near call if it's right there. */
    private fun coverFollowUp(i: Int, text: String, maneuverStart: DoubleArray, near: Double) {
        if (!text.contains("Then ", ignoreCase = false) || i + 1 >= maneuverStart.size) return
        spoken += (i + 1) to Stage.FAR
        if (maneuverStart[i + 1] - maneuverStart[i] < near * 1.5) spoken += (i + 1) to Stage.NEAR
    }

    fun spokenDistance(meters: Double): String = if (imperial()) {
        val miles = meters / 1609.344
        if (miles < 0.2) "${((meters * 3.28084) / 100).roundToInt().coerceAtLeast(1) * 100} feet"
        else if (miles < 1.05) "%.1f miles".format(Locale.UK, miles).replace("1.0 miles", "1 mile")
        else "${miles.roundToInt()} miles"
    } else {
        when {
            meters < 1_000 -> "${((meters / 50).roundToInt() * 50).coerceAtLeast(50)} metres"
            meters < 1_050 -> "1 kilometre"
            meters < 10_000 -> "%.1f kilometres".format(Locale.UK, meters / 1000).replace(".0 ", " ")
            else -> "${(meters / 1000).roundToInt()} kilometres"
        }
    }

    private fun String.lowerFirst(): String = if (isEmpty()) this else this[0].lowercaseChar() + substring(1)

    private companion object {
        const val NEAR_SECONDS = 7.0
        const val FAR_SECONDS = 30.0
    }
}

package app.woozy.traffic

/**
 * Decides when to speak a speeding warning: only after speeding for [graceMillis] (ignores brief
 * overshoots), then at most once per [cooldownMillis] — unless the limit changes (e.g. 100 → 80),
 * which warrants a new warning. Pure logic; the caller speaks.
 */
class SpeedingAlerter(
    private val graceMillis: Long = 3_000L,
    private val cooldownMillis: Long = 60_000L,
) {
    private var speedingSince: Long? = null
    private var lastAlertAt: Long? = null
    private var lastAlertLimit: Int? = null

    /** @return true when a warning should be spoken now. */
    fun update(isSpeeding: Boolean, limit: Int?, nowMillis: Long): Boolean {
        if (!isSpeeding || limit == null) {
            speedingSince = null
            return false
        }
        val since = speedingSince ?: nowMillis.also { speedingSince = it }
        if (nowMillis - since < graceMillis) return false
        val last = lastAlertAt
        val due = last == null || nowMillis - last >= cooldownMillis || limit != lastAlertLimit
        if (due) {
            lastAlertAt = nowMillis
            lastAlertLimit = limit
        }
        return due
    }
}

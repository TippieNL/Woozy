package app.woozy.voice

import app.woozy.domain.model.ReportType
import java.util.Locale

sealed interface VoiceCommand {
    data class Report(val type: ReportType) : VoiceCommand
    data class Search(val query: String) : VoiceCommand
    data class NavigateFavorite(val favorite: Favorite) : VoiceCommand
    data object Cancel : VoiceCommand
    data object Repeat : VoiceCommand
    data object Unknown : VoiceCommand

    enum class Favorite { HOME, WORK }
}

/**
 * Maps a speech transcript to a command. English + Dutch keywords. Anything that isn't a known
 * command is treated as a destination search, which is what users mostly say.
 */
object VoiceCommandParser {

    // Order matters: more specific phrases first ("speed camera" before "camera", "mobile" before "camera").
    private val reportKeywords: List<Pair<ReportType, List<String>>> = listOf(
        ReportType.MOBILE_SPEED_TRAP to listOf("mobile", "speed trap", "radar", "laser", "flitser", "controle", "snelheidscontrole"),
        ReportType.SPEED_CAMERA to listOf("speed camera", "camera", "flitspaal"),
        ReportType.ACCIDENT to listOf("accident", "crash", "collision", "ongeluk", "ongeval", "aanrijding"),
        ReportType.TRAFFIC_JAM to listOf("traffic jam", "jam", "traffic", "congestion", "file"),
        ReportType.ROADWORKS to listOf("roadworks", "road works", "roadwork", "construction", "werkzaamheden", "wegwerkzaamheden"),
        ReportType.STOPPED_VEHICLE to listOf("broken down", "breakdown", "stopped vehicle", "stopped car", "pechgeval", "pech", "stilstaand"),
        ReportType.OBJECT_ON_ROAD to listOf("object", "debris", "voorwerp", "obstakel"),
        ReportType.POLICE to listOf("police", "politie"),
        ReportType.ROAD_HAZARD to listOf("hazard", "slippery", "ice", "gladheid", "gevaar op de weg"),
        ReportType.OTHER to listOf("danger", "dangerous", "gevaarlijk", "gevaar"),
    )

    private val reportPrefixes = listOf("report", "meld", "melding", "rapporteer")
    private val navigatePrefixes = listOf(
        "navigate to", "navigate", "drive to", "take me to", "go to", "directions to", "route to",
        "navigeer naar", "rij naar", "ga naar", "route naar", "navigeer",
    )
    private val cancelPhrases = listOf("cancel", "stop navigation", "stop navigating", "end navigation", "annuleer", "stop navigatie", "stoppen")
    private val repeatPhrases = listOf("repeat", "say again", "what did you say", "herhaal", "nog een keer")
    private val homeWords = listOf("home", "huis", "naar huis", "thuis")
    private val workWords = listOf("work", "werk", "kantoor", "office")

    fun parse(transcript: String): VoiceCommand {
        val original = transcript.trim().replace(TRAILING_PUNCTUATION, "")
        val text = original.lowercase(Locale.ROOT)
        if (text.isBlank()) return VoiceCommand.Unknown

        if (repeatPhrases.any { text == it || text.startsWith("$it ") }) return VoiceCommand.Repeat
        if (cancelPhrases.any { text == it || text.startsWith("$it ") }) return VoiceCommand.Cancel

        reportPrefixes.firstOrNull { text == it || text.startsWith("$it ") }?.let { prefix ->
            val rest = text.removePrefix(prefix).trim().removePrefix("a ").removePrefix("an ").removePrefix("een ").trim()
            return reportTypeFor(rest)?.let { VoiceCommand.Report(it) } ?: VoiceCommand.Unknown
        }

        navigatePrefixes.firstOrNull { text.startsWith("$it ") || text == it }?.let { prefix ->
            val target = text.removePrefix(prefix).trim().removePrefix("to ").removePrefix("naar ").trim()
            if (target.isEmpty()) return VoiceCommand.Unknown
            favoriteFor(target)?.let { return VoiceCommand.NavigateFavorite(it) }
            // Keep the user's original casing for the search query.
            return VoiceCommand.Search(original.takeLast(target.length))
        }
        favoriteFor(text)?.let { return VoiceCommand.NavigateFavorite(it) }
        return VoiceCommand.Search(original)
    }

    private val TRAILING_PUNCTUATION = Regex("[.!?,]+$")

    private fun reportTypeFor(phrase: String): ReportType? =
        reportKeywords.firstOrNull { (_, words) -> words.any { phrase.contains(it) } }?.first

    private fun favoriteFor(target: String): VoiceCommand.Favorite? = when {
        target.removeSuffix(" toe") in homeWords || target in listOf("go home", "ga naar huis") -> VoiceCommand.Favorite.HOME
        target in workWords || target == "the office" -> VoiceCommand.Favorite.WORK
        else -> null
    }
}

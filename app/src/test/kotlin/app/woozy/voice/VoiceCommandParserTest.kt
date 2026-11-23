package app.woozy.voice

import app.woozy.domain.model.ReportType
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {

    private fun parse(s: String) = VoiceCommandParser.parse(s)

    @Test
    fun reports() {
        assertEquals(VoiceCommand.Report(ReportType.ACCIDENT), parse("Report accident"))
        assertEquals(VoiceCommand.Report(ReportType.TRAFFIC_JAM), parse("report a traffic jam"))
        assertEquals(VoiceCommand.Report(ReportType.MOBILE_SPEED_TRAP), parse("report mobile speed camera"))
        assertEquals(VoiceCommand.Report(ReportType.SPEED_CAMERA), parse("report speed camera"))
        assertEquals(VoiceCommand.Report(ReportType.POLICE), parse("report police"))
        assertEquals(VoiceCommand.Report(ReportType.OTHER), parse("report dangerous situation"))
        assertEquals(VoiceCommand.Report(ReportType.TRAFFIC_JAM), parse("meld file"))
        assertEquals(VoiceCommand.Report(ReportType.ACCIDENT), parse("Meld een ongeluk"))
        assertEquals(VoiceCommand.Unknown, parse("report something weird"))
    }

    @Test
    fun navigation() {
        assertEquals(VoiceCommand.Search("Amsterdam"), parse("Navigate to Amsterdam."))
        assertEquals(VoiceCommand.Search("Utrecht Centraal"), parse("navigeer naar Utrecht Centraal"))
        assertEquals(VoiceCommand.NavigateFavorite(VoiceCommand.Favorite.HOME), parse("Navigate home"))
        assertEquals(VoiceCommand.NavigateFavorite(VoiceCommand.Favorite.WORK), parse("navigate to work"))
        assertEquals(VoiceCommand.NavigateFavorite(VoiceCommand.Favorite.HOME), parse("rij naar huis"))
        assertEquals(VoiceCommand.Search("Albert Heijn Zwolle"), parse("Albert Heijn Zwolle"))
    }

    @Test
    fun controlCommands() {
        assertEquals(VoiceCommand.Cancel, parse("Cancel navigation"))
        assertEquals(VoiceCommand.Cancel, parse("stop navigatie"))
        assertEquals(VoiceCommand.Repeat, parse("Repeat warning"))
        assertEquals(VoiceCommand.Unknown, parse("   "))
    }
}

package app.woozy.ui.reports

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.woozy.R
import app.woozy.domain.model.ReportType

/** Visual identity per report type, shared by Compose UI and map markers. */
data class ReportTypeStyle(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    /** ARGB colour of the badge / marker. */
    val color: Long,
)

val ReportType.style: ReportTypeStyle
    get() = when (this) {
        ReportType.SPEED_CAMERA -> ReportTypeStyle(R.string.report_type_speed_camera, R.drawable.ic_report_camera, 0xFF138BFF)
        ReportType.MOBILE_SPEED_TRAP -> ReportTypeStyle(R.string.report_type_mobile_trap, R.drawable.ic_report_mobile_trap, 0xFF5E5CE6)
        ReportType.ACCIDENT -> ReportTypeStyle(R.string.report_type_accident, R.drawable.ic_report_accident, 0xFFFF453A)
        ReportType.TRAFFIC_JAM -> ReportTypeStyle(R.string.report_type_jam, R.drawable.ic_report_jam, 0xFFD93A2F)
        ReportType.ROADWORKS -> ReportTypeStyle(R.string.report_type_roadworks, R.drawable.ic_report_roadworks, 0xFFFF8A00)
        ReportType.ROAD_HAZARD -> ReportTypeStyle(R.string.report_type_hazard, R.drawable.ic_report_hazard, 0xFFF2A900)
        ReportType.STOPPED_VEHICLE -> ReportTypeStyle(R.string.report_type_stopped, R.drawable.ic_report_stopped, 0xFFF2A900)
        ReportType.OBJECT_ON_ROAD -> ReportTypeStyle(R.string.report_type_object, R.drawable.ic_report_object, 0xFFF2A900)
        ReportType.POLICE -> ReportTypeStyle(R.string.report_type_police, R.drawable.ic_report_police, 0xFF2F6BFF)
        ReportType.OTHER -> ReportTypeStyle(R.string.report_type_danger, R.drawable.ic_report_danger, 0xFFFF453A)
    }

/** Order shown in the report sheet: most-used first. */
val ReportSheetOrder = listOf(
    ReportType.MOBILE_SPEED_TRAP,
    ReportType.TRAFFIC_JAM,
    ReportType.ACCIDENT,
    ReportType.SPEED_CAMERA,
    ReportType.POLICE,
    ReportType.ROADWORKS,
    ReportType.ROAD_HAZARD,
    ReportType.STOPPED_VEHICLE,
    ReportType.OBJECT_ON_ROAD,
    ReportType.OTHER,
)

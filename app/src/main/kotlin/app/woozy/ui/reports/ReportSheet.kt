package app.woozy.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.woozy.R
import app.woozy.domain.model.ReportType
import app.woozy.ui.components.IconBadge
import app.woozy.ui.theme.WoozyColors

/** Grid of large report tiles. One tap reports at the current GPS position and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(onReport: (ReportType) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.PartiallyExpanded))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WoozyColors.PanelLow,
        contentColor = WoozyColors.TextPrimary,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.report_subtitle), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            ReportSheetOrder.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { type ->
                        ReportTile(type, Modifier.weight(1f)) { onReport(type) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ReportTile(type: ReportType, modifier: Modifier, onClick: () -> Unit) {
    val style = type.style
    Surface(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(20.dp),
        color = WoozyColors.PanelHigh,
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IconBadge(style.icon, Color(style.color), size = 50.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(style.label),
                fontSize = 13.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                color = WoozyColors.TextPrimary,
                maxLines = 2,
            )
        }
    }
}

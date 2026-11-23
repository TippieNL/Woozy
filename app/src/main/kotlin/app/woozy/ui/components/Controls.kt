package app.woozy.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.woozy.R
import app.woozy.ui.theme.WoozyColors

/** Round floating map button (voice toggle, recenter). 56dp+ touch target. */
@Composable
fun RoundMapButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = WoozyColors.Accent,
    size: Dp = 60.dp,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size).shadow(8.dp, CircleShape),
        shape = CircleShape,
        color = WoozyColors.PanelHigh,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(28.dp))
        }
    }
}

enum class MainTab { MENU, LIST, MAP }

/** Bottom navigation: menu icon on the left, List / Map pills in the centre. */
@Composable
fun WoozyBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WoozyColors.Black)
            .navigationBarsPadding()
            .height(72.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(role = Role.Tab, onClickLabel = stringResource(R.string.tab_menu)) { onSelect(MainTab.MENU) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_menu),
                contentDescription = stringResource(R.string.tab_menu),
                tint = if (selected == MainTab.MENU) WoozyColors.Accent else WoozyColors.TextSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            TabPill(stringResource(R.string.tab_list), selected == MainTab.LIST) { onSelect(MainTab.LIST) }
            Spacer(Modifier.width(12.dp))
            TabPill(stringResource(R.string.tab_map), selected == MainTab.MAP) { onSelect(MainTab.MAP) }
        }
        Spacer(Modifier.size(56.dp)) // balances the menu button so the pills stay centred
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(52.dp)
            .width(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) WoozyColors.Pill else Color.Transparent)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) WoozyColors.TextPrimary else WoozyColors.TextSecondary,
        )
    }
}

/** Small rounded status chip with a coloured dot (GPS / connectivity state). */
@Composable
fun StatusChip(text: String, dotColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(WoozyColors.PanelHigh.copy(alpha = 0.94f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WoozyColors.TextPrimary)
    }
}

/** Colored circular badge with a white glyph — used for report types in lists and the sheet. */
@Composable
fun IconBadge(@DrawableRes icon: Int, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

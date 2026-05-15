package com.mustafa.sinavtakvim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Button as UnstyledButton

object CorporateColors {
    val Primary: Color
        @Composable get() = MaterialTheme.colors.primary

    val Steel: Color
        @Composable get() = if (MaterialTheme.colors.isLight) Color(0xFF516B7A) else Color(0xFF8AA8BC)

    val Amber: Color
        @Composable get() = MaterialTheme.colors.secondary

    val Risk: Color
        @Composable get() = if (MaterialTheme.colors.isLight) Color(0xFF9B4E36) else Color(0xFFE08D73)

    val Success: Color
        @Composable get() = if (MaterialTheme.colors.isLight) Color(0xFF2F6B4F) else Color(0xFF70C59B)

    val Background: Color
        @Composable get() = MaterialTheme.colors.background

    val Surface: Color
        @Composable get() = MaterialTheme.colors.surface

    val Ink: Color
        @Composable get() = MaterialTheme.colors.onSurface

    val Muted: Color
        @Composable get() = if (MaterialTheme.colors.isLight) Color(0xFF66727A) else Color(0xFF9FAAB4)

    val Border: Color
        @Composable get() = if (MaterialTheme.colors.isLight) Color(0xFFE0E5E8) else Color(0xFF2A333B)

    val PrimarySoft: Color
        @Composable get() = if (MaterialTheme.colors.isLight) Color(0xFFE5EEEA) else Color(0xFF20342F)
}

enum class OpsButtonStyle {
    Primary,
    Secondary,
    Neutral,
    Danger,
    Quiet
}

@Composable
fun OpsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    style: OpsButtonStyle = OpsButtonStyle.Primary
) {
    val background = when (style) {
        OpsButtonStyle.Primary -> CorporateColors.Primary
        OpsButtonStyle.Secondary -> CorporateColors.Steel
        OpsButtonStyle.Neutral -> CorporateColors.Surface
        OpsButtonStyle.Danger -> CorporateColors.Surface
        OpsButtonStyle.Quiet -> Color.Transparent
    }
    val content = when (style) {
        OpsButtonStyle.Primary,
        OpsButtonStyle.Secondary -> Color.White
        OpsButtonStyle.Danger -> CorporateColors.Risk
        OpsButtonStyle.Quiet -> CorporateColors.Primary
        OpsButtonStyle.Neutral -> CorporateColors.Ink
    }
    val border = when (style) {
        OpsButtonStyle.Neutral -> CorporateColors.Border
        OpsButtonStyle.Danger -> CorporateColors.Risk
        else -> Color.Transparent
    }

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        backgroundColor = if (enabled) background else CorporateColors.Border.copy(alpha = 0.55f),
        contentColor = if (enabled) content else CorporateColors.Muted,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        shape = MaterialTheme.shapes.medium,
        borderColor = border,
        borderWidth = if (border == Color.Transparent) 0.dp else 1.dp
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) content else CorporateColors.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) content else CorporateColors.Muted
        )
    }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.h1, color = CorporateColors.Ink)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
        }
        if (trailing != null) {
            Spacer(Modifier.width(14.dp))
            trailing()
        }
    }
}

@Composable
fun CorporateCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        elevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        backgroundColor = CorporateColors.Surface
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    caption: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.h3, color = CorporateColors.Ink)
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Text(caption, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    caption: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    CorporateCard(modifier.heightIn(min = 118.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
            Surface(
                modifier = Modifier.width(32.dp).height(4.dp),
                color = accent,
                shape = MaterialTheme.shapes.small,
                content = {}
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(value, style = MaterialTheme.typography.h1, color = CorporateColors.Ink)
        Spacer(Modifier.height(4.dp))
        Text(caption, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.11f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Divider(modifier = modifier, color = CorporateColors.Border, thickness = 1.dp)
}

@Composable
fun ProgressRow(
    label: String,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val safeValue = value.coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
            Text("${(safeValue * 100).toInt()}%", style = MaterialTheme.typography.body2, color = CorporateColors.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(CorporateColors.Border, MaterialTheme.shapes.small)
        ) {
            Spacer(
                Modifier
                    .fillMaxWidth(safeValue.coerceAtLeast(0.02f))
                    .height(7.dp)
                    .background(color, MaterialTheme.shapes.small)
            )
        }
    }
}

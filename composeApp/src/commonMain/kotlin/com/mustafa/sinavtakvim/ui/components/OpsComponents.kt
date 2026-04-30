package com.mustafa.sinavtakvim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object CorporateColors {
    val Background = Color(0xFFF2F4F6)
    val Surface = Color.White
    val Ink = Color(0xFF172026)
    val Muted = Color(0xFF66727A)
    val Border = Color(0xFFE0E5E8)
    val Primary = Color(0xFF17443D)
    val PrimarySoft = Color(0xFFE5EEEA)
    val Steel = Color(0xFF516B7A)
    val Amber = Color(0xFFB88343)
    val Risk = Color(0xFF9B4E36)
    val Success = Color(0xFF2F6B4F)
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
                .background(Color(0xFFE7ECEF), MaterialTheme.shapes.small)
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

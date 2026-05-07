package com.mustafa.sinavtakvim.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun ResponsiveBox(
    modifier: Modifier = Modifier,
    breakpoint: Dp,
    content: @Composable (isExpanded: Boolean) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        content(maxWidth > breakpoint)
    }
}

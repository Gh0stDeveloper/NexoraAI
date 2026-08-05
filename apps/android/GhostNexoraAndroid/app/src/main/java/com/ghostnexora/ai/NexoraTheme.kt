package com.ghostnexora.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

internal val NexoraBackground = Color(0xFF06080E)
internal val NexoraSurface = Color(0xFF101722)
internal val NexoraSurfaceElevated = Color(0xFF151F2C)
internal val NexoraAccent = Color(0xFF38E8B0)
internal val NexoraMuted = Color(0xFF94A3B8)

@Composable
internal fun NexoraMark(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(NexoraAccent, Color(0xFF17A77D))),
                RoundedCornerShape(size * 0.28f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "N",
            color = Color(0xFF03130E),
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

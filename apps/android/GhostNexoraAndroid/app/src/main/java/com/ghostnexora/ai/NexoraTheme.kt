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

internal val NexoraBackground = Color(0xFF090B10)
internal val NexoraSurface = Color(0xFF12151C)
internal val NexoraSurfaceElevated = Color(0xFF1A1E27)
internal val NexoraAccent = Color(0xFF35D6AE)
internal val NexoraAccentStrong = Color(0xFF10A37F)
internal val NexoraViolet = Color(0xFF8B7CFF)
internal val NexoraBlue = Color(0xFF69A7FF)
internal val NexoraMuted = Color(0xFF98A0AE)
internal val NexoraDivider = Color(0xFF252A35)

@Composable
internal fun NexoraMark(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF43E7BC),
                        NexoraAccentStrong,
                        Color(0xFF4B7CFF),
                    ),
                ),
                RoundedCornerShape(size * 0.30f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "N",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

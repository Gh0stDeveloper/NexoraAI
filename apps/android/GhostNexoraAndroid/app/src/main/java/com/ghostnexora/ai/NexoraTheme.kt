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

internal val NexoraBackground = Color(0xFF0D0D0D)
internal val NexoraSurface = Color(0xFF171717)
internal val NexoraSurfaceElevated = Color(0xFF212121)
internal val NexoraAccent = Color(0xFF10A37F)
internal val NexoraMuted = Color(0xFFA3A3A3)

@Composable
internal fun NexoraMark(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(Color(0xFF35D6AE), NexoraAccent)),
                RoundedCornerShape(size * 0.28f),
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

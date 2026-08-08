package com.ghostnexora.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil.compose.AsyncImage

@Composable
internal fun NexoraUserAvatar(
    user: NexoraUser,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        NexoraAccent.copy(alpha = 0.42f),
                        NexoraViolet.copy(alpha = 0.36f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = user.name.trim().firstOrNull()?.uppercase() ?: "N",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.34f).sp,
        )
        user.imageUrl?.takeIf { it.startsWith("https://") }?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = "Foto de perfil de ${user.name}",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

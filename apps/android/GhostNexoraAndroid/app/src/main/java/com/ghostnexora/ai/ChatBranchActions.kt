package com.ghostnexora.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ChatBranchActions(
    message: ChatMessage,
    busy: Boolean,
    variantPosition: Int?,
    variantTotal: Int,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onBranch: () -> Unit,
    onPreviousVariant: () -> Unit,
    onNextVariant: () -> Unit,
) {
    if (message.content.isBlank()) return
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.025f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.055f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isUser) {
                    TextButton(onClick = onEdit, enabled = !busy) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(5.dp))
                        Text("Editar", fontSize = 11.sp)
                    }
                } else {
                    TextButton(onClick = onRegenerate, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(5.dp))
                        Text("Regenerar", fontSize = 11.sp)
                    }
                }
                IconButton(
                    onClick = onBranch,
                    enabled = !busy,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Default.CallSplit,
                        contentDescription = "Ramificar conversación",
                        modifier = Modifier.size(17.dp),
                    )
                }
                if (variantPosition != null && variantTotal > 1) {
                    IconButton(
                        onClick = onPreviousVariant,
                        enabled = variantPosition > 1,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Versión anterior",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        "$variantPosition/$variantTotal",
                        color = NexoraMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onNextVariant,
                        enabled = variantPosition < variantTotal,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Versión siguiente",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

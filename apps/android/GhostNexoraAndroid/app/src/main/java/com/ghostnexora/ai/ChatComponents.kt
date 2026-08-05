package com.ghostnexora.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DrawerContent(
    sessions: List<ChatSession>,
    activeSessionId: String,
    onNewChat: () -> Unit,
    onSelect: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NexoraMark(42.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text("Historial", fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text("Chats guardados en este dispositivo", color = NexoraMuted, fontSize = 12.sp)
            }
        }

        Button(
            onClick = onNewChat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Nuevo chat")
        }

        Divider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = Color.White.copy(alpha = 0.08f),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(sessions, key = { it.id }) { session ->
                val selected = session.id == activeSessionId
                ListItem(
                    headlineContent = {
                        Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text("${session.model.label} · ${session.messages.size} mensajes", maxLines = 1)
                    },
                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { onDelete(session) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar chat")
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (selected) {
                            NexoraAccent.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(session) },
                )
            }
        }
    }
}

@Composable
internal fun EmptyChatState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NexoraMark(78.dp)
        Text("¿Qué quieres construir hoy?", fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(
            "Escribe una solicitud o usa + para añadir imágenes, archivos y elegir el modelo.",
            color = NexoraMuted,
            fontSize = 15.sp,
        )
    }
}

@Composable
internal fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.96f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF116C58) else Color(0xE6111824),
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (isUser) "Tú" else "Nexora AI",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                message.attachmentNames.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            name,
                            color = Color(0xFFD5E5E0),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
                Text(message.content, color = Color(0xFFEAF0EE), lineHeight = 22.sp)
            }
        }
    }
}

@Composable
internal fun AssistantThinking(label: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE6111824)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Column {
                Text("Nexora AI está analizando", fontWeight = FontWeight.Bold)
                Text(label, color = NexoraMuted, fontSize = 12.sp)
            }
        }
    }
}

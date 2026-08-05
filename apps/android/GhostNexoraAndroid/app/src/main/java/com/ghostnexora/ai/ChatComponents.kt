package com.ghostnexora.ai

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    projects: List<ChatProject>,
    activeSessionId: String,
    onNewChat: (String?) -> Unit,
    onNewProject: () -> Unit,
    onSelect: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
    onTogglePinSession: (ChatSession) -> Unit,
    onDeleteProject: (ChatProject) -> Unit,
    onTogglePinProject: (ChatProject) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val orderedProjects = projects.sortedWith(
        compareByDescending<ChatProject> { it.isPinned }.thenByDescending { it.updatedAt },
    )
    val pinnedChats = sessions.filter { it.isPinned }.sortedByDescending { it.updatedAt }
    val independentChats = sessions
        .filter { it.projectId == null }
        .sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAt })

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
                Text("Nexora AI", fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text("Chats, fijados y proyectos", color = NexoraMuted, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onNewChat(null) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Chat")
            }
            FilledTonalButton(
                onClick = onNewProject,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Proyecto")
            }
        }

        Divider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = Color.White.copy(alpha = 0.08f),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (pinnedChats.isNotEmpty()) {
                item { DrawerSectionLabel("Fijados") }
                items(pinnedChats, key = { "pinned-${it.id}" }) { session ->
                    SessionRow(
                        session = session,
                        selected = session.id == activeSessionId,
                        onSelect = onSelect,
                        onDelete = onDelete,
                        onTogglePin = onTogglePinSession,
                    )
                }
            }

            if (orderedProjects.isNotEmpty()) {
                item { DrawerSectionLabel("Proyectos") }
                orderedProjects.forEach { project ->
                    item(key = "project-${project.id}") {
                        ProjectRow(
                            project = project,
                            chatCount = sessions.count { it.projectId == project.id },
                            onNewChat = { onNewChat(project.id) },
                            onDelete = onDeleteProject,
                            onTogglePin = onTogglePinProject,
                        )
                    }
                    items(
                        sessions
                            .filter { it.projectId == project.id }
                            .sortedWith(
                                compareByDescending<ChatSession> { it.isPinned }
                                    .thenByDescending { it.updatedAt },
                            ),
                        key = { "project-chat-${it.id}" },
                    ) { session ->
                        SessionRow(
                            session = session,
                            selected = session.id == activeSessionId,
                            nested = true,
                            onSelect = onSelect,
                            onDelete = onDelete,
                            onTogglePin = onTogglePinSession,
                        )
                    }
                }
            }

            item { DrawerSectionLabel("Chats") }
            items(independentChats, key = { "chat-${it.id}" }) { session ->
                SessionRow(
                    session = session,
                    selected = session.id == activeSessionId,
                    onSelect = onSelect,
                    onDelete = onDelete,
                    onTogglePin = onTogglePinSession,
                )
            }
        }

        Divider(color = Color.White.copy(alpha = 0.08f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilledTonalButton(onClick = onOpenTerms, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(5.dp))
                Text("Términos", fontSize = 12.sp)
            }
            FilledTonalButton(onClick = onOpenPrivacy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PrivacyTip, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(5.dp))
                Text("Privacidad", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(label: String) {
    Text(
        label.uppercase(),
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 5.dp),
        color = NexoraMuted,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun SessionRow(
    session: ChatSession,
    selected: Boolean,
    nested: Boolean = false,
    onSelect: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
    onTogglePin: (ChatSession) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text("${session.model.label} · ${session.messages.size} mensajes", maxLines = 1)
        },
        leadingContent = {
            Icon(
                if (session.isPinned) Icons.Default.PushPin else Icons.Default.History,
                contentDescription = null,
                tint = if (session.isPinned) NexoraAccent else NexoraMuted,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onTogglePin(session) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.PushPin, contentDescription = "Fijar o soltar chat")
                }
                IconButton(onClick = { onDelete(session) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar chat")
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) NexoraAccent.copy(alpha = 0.12f) else Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (nested) 18.dp else 0.dp)
            .clickable { onSelect(session) },
    )
}

@Composable
private fun ProjectRow(
    project: ChatProject,
    chatCount: Int,
    onNewChat: () -> Unit,
    onDelete: (ChatProject) -> Unit,
    onTogglePin: (ChatProject) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(project.name, maxLines = 1, fontWeight = FontWeight.Bold)
        },
        supportingContent = { Text("$chatCount chats") },
        leadingContent = {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = if (project.isPinned) NexoraAccent else Color(0xFF7DD3FC),
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onNewChat, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo chat en proyecto")
                }
                IconButton(onClick = { onTogglePin(project) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.PushPin, contentDescription = "Fijar o soltar proyecto")
                }
                IconButton(onClick = { onDelete(project) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar proyecto")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color(0xFF101925)),
    )
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
            "Describe una tarea o usa + para añadir archivos, elegir especialistas y activar pruebas aisladas.",
            color = NexoraMuted,
            fontSize = 15.sp,
        )
        Surface(
            color = NexoraAccent.copy(alpha = 0.08f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NexoraAccent.copy(alpha = 0.2f)),
        ) {
            Text(
                "El progreso muestra etapas y tiempos, no el razonamiento privado del modelo.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = Color(0xFFC7F9E9),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    var detailsVisible by remember(message.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.98f),
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
                        Text(name, color = Color(0xFFD5E5E0), fontSize = 12.sp, maxLines = 1)
                    }
                }
                Text(message.content, color = Color(0xFFEAF0EE), lineHeight = 22.sp)

                if (!isUser && message.elapsedMs != null) {
                    Divider(color = Color.White.copy(alpha = 0.08f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detailsVisible = !detailsVisible },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Pensó ${formatDuration(message.elapsedMs)} · ${message.agentsUsed ?: 0} agente(s)",
                            modifier = Modifier.weight(1f),
                            color = NexoraMuted,
                            fontSize = 12.sp,
                        )
                        Icon(
                            if (detailsVisible) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Mostrar actividad",
                            tint = NexoraMuted,
                        )
                    }
                    AnimatedVisibility(detailsVisible) {
                        ExecutionDetails(message.trace, message.codeValidation)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionDetails(
    trace: List<AgentProgress>,
    validation: CodeValidationSummary?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        trace
            .filter { it.status == "completed" }
            .distinctBy { it.stage to it.agent }
            .takeLast(8)
            .forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NexoraAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(step.label, color = Color(0xFFC6D2D0), fontSize = 12.sp)
                }
            }
        validation?.let {
            Surface(
                color = Color.Black.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = NexoraAccent)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Laboratorio: ${it.status}${it.language?.let { language -> " · $language" } ?: ""}",
                        color = Color(0xFFD9E6E2),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AssistantThinking(
    label: String,
    elapsedMs: Long,
    progress: List<AgentProgress>,
) {
    val active = progress.lastOrNull { it.status == "active" } ?: progress.lastOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(0.98f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE6111824)),
        border = BorderStroke(1.dp, NexoraAccent.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.RadioButtonChecked,
                    contentDescription = null,
                    tint = NexoraAccent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Nexora AI está pensando", fontWeight = FontWeight.Bold)
                    Text(active?.label ?: label, color = NexoraMuted, fontSize = 12.sp)
                }
                Text(formatDuration(elapsedMs), color = NexoraAccent, fontWeight = FontWeight.Black)
            }
            val total = active?.totalSteps?.coerceAtLeast(1) ?: 1
            val current = active?.step?.coerceIn(0, total) ?: 0
            LinearProgressIndicator(
                progress = { current.toFloat() / total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            progress
                .filter { it.status == "completed" }
                .distinctBy { it.stage to it.agent }
                .takeLast(4)
                .forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NexoraAccent,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.size(7.dp))
                        Text(step.label, color = Color(0xFFC6D2D0), fontSize = 11.sp)
                    }
                }
        }
    }
}

internal fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000.0
    return if (seconds < 60) String.format("%.1f s", seconds) else {
        val minutes = (seconds / 60).toInt()
        val remaining = (seconds % 60).toInt()
        "${minutes}m ${remaining}s"
    }
}

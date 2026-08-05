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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
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
    var search by remember { mutableStateOf("") }
    val visibleSessions = if (search.isBlank()) {
        sessions
    } else {
        sessions.filter { it.title.contains(search.trim(), ignoreCase = true) }
    }
    val orderedProjects = projects.sortedWith(
        compareByDescending<ChatProject> { it.isPinned }.thenByDescending { it.updatedAt },
    )
    val pinnedChats = visibleSessions.filter { it.isPinned }.sortedByDescending { it.updatedAt }
    val independentChats = visibleSessions
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

        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(60) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            placeholder = { Text("Buscar en el historial") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )

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

        HorizontalDivider(
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
                        visibleSessions
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

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
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
    val pending = session.messages.any {
        it.requestStatus == RequestStatus.QUEUED ||
            it.requestStatus == RequestStatus.PROCESSING
    }
    ListItem(
        headlineContent = {
            Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                if (pending) {
                    "Respuesta en proceso · puedes volver después"
                } else {
                    "${session.model.label} · ${session.messages.size} mensajes"
                },
                maxLines = 1,
            )
        },
        leadingContent = {
            Icon(
                when {
                    pending -> Icons.Default.RadioButtonChecked
                    session.isPinned -> Icons.Default.PushPin
                    else -> Icons.Default.History
                },
                contentDescription = null,
                tint = if (pending || session.isPinned) NexoraAccent else NexoraMuted,
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
internal fun EmptyChatState(
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit,
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NexoraMark(78.dp)
        Text("¿En qué puedo ayudarte?", fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text(
            "Conversa con el asistente o cambia de especialidad cuando quieras crear, revisar o desplegar algo.",
            color = NexoraMuted,
            fontSize = 15.sp,
        )
        listOf(
            "Ayúdame a convertir una idea en un plan claro",
            "Revisa este problema paso a paso",
            "Diseña una aplicación Android moderna",
        ).forEach { suggestion ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSuggestion(suggestion) },
                color = Color.White.copy(alpha = 0.045f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NexoraAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(suggestion, color = Color(0xFFE4ECE9), fontSize = 13.sp)
                }
            }
        }
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
internal fun MessageBubble(
    message: ChatMessage,
    userBuildsEnabled: Boolean,
    onBuild: (ChatMessage) -> Unit,
    onDownload: (String) -> Unit,
) {
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
                containerColor = if (isUser) Color(0xFF2F2F2F) else Color(0xF2171717),
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

                if (!isUser) {
                    message.buildArtifact?.let { artifact ->
                        AndroidBuildCard(artifact = artifact, onDownload = onDownload)
                    } ?: if (
                        userBuildsEnabled &&
                        message.content.isNotBlank() &&
                        message.requestStatus != RequestStatus.FAILED
                    ) {
                        FilledTonalButton(
                            onClick = { onBuild(message) },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(
                                Icons.Default.Android,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(7.dp))
                            Text("Crear APK con esta respuesta")
                        }
                    }
                }

                if (!isUser && message.elapsedMs != null) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
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
private fun AndroidBuildCard(
    artifact: AndroidBuildArtifact,
    onDownload: (String) -> Unit,
) {
    val completed = artifact.status == AndroidBuildStatus.COMPLETED
    val failed = artifact.status == AndroidBuildStatus.FAILED ||
        artifact.status == AndroidBuildStatus.EXPIRED
    Surface(
        color = when {
            completed -> NexoraAccent.copy(alpha = 0.10f)
            failed -> Color(0xFFEF4444).copy(alpha = 0.10f)
            else -> Color.White.copy(alpha = 0.045f)
        },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            when {
                completed -> NexoraAccent.copy(alpha = 0.28f)
                failed -> Color(0xFFEF4444).copy(alpha = 0.30f)
                else -> Color.White.copy(alpha = 0.10f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        completed -> Icons.Default.CheckCircle
                        failed -> Icons.Default.ErrorOutline
                        else -> Icons.Default.Schedule
                    },
                    contentDescription = null,
                    tint = when {
                        completed -> NexoraAccent
                        failed -> Color(0xFFF87171)
                        else -> Color(0xFF93C5FD)
                    },
                )
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(artifact.appName, fontWeight = FontWeight.Bold)
                    Text(artifact.progressLabel, color = NexoraMuted, fontSize = 12.sp)
                }
            }
            if (!completed && !failed) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (completed) {
                Text(
                    "Firma ${artifact.signatureSchemes.joinToString(" + ")} · disponible durante 1 hora",
                    color = Color(0xFFC8D8D3),
                    fontSize = 11.sp,
                )
                artifact.downloadUrl?.let { url ->
                    Button(
                        onClick = { onDownload(url) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.size(7.dp))
                        Text("Descargar APK")
                    }
                }
            }
            artifact.error?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp) }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xF2171717)),
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

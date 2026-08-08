package com.ghostnexora.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val query = search.trim()
    val visibleSessions = if (query.isBlank()) {
        sessions
    } else {
        sessions.filter { session ->
            session.title.contains(query, ignoreCase = true) ||
                session.messages.any { it.content.contains(query, ignoreCase = true) }
        }
    }
    val orderedProjects = projects.sortedWith(
        compareByDescending<ChatProject> { it.isPinned }.thenByDescending { it.updatedAt },
    )
    val pinnedChats = visibleSessions.filter { it.isPinned }.sortedByDescending { it.updatedAt }
    val independentChats = visibleSessions
        .filter { it.projectId == null }
        .sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAt })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10131A), Color(0xFF0B0D12)),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NexoraMark(44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text("Nexora AI", fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text(
                    "${sessions.size} chats · ${projects.size} proyectos",
                    color = NexoraMuted,
                    fontSize = 11.sp,
                )
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(80) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            placeholder = { Text("Buscar chats y mensajes") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onNewChat(null) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(17.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Nuevo chat")
            }
            FilledTonalButton(
                onClick = onNewProject,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(17.dp),
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Proyecto")
            }
        }

        HorizontalDivider(color = NexoraDivider)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (query.isNotBlank() && visibleSessions.isEmpty()) {
                item {
                    Text(
                        "No encontramos resultados para “$query”",
                        modifier = Modifier.padding(16.dp),
                        color = NexoraMuted,
                        fontSize = 12.sp,
                    )
                }
            }

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

        HorizontalDivider(color = NexoraDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            TextButton(onClick = onOpenTerms, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(5.dp))
                Text("Términos", fontSize = 12.sp)
            }
            TextButton(onClick = onOpenPrivacy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PrivacyTip, contentDescription = null, modifier = Modifier.size(16.dp))
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
        modifier = Modifier.padding(start = 12.dp, top = 13.dp, bottom = 5.dp),
        color = NexoraMuted,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        letterSpacing = 1.15.sp,
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
        it.requestStatus == RequestStatus.QUEUED || it.requestStatus == RequestStatus.PROCESSING
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (nested) 16.dp else 0.dp)
            .clickable { onSelect(session) },
        color = if (selected) NexoraAccent.copy(alpha = 0.11f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(1.dp, NexoraAccent.copy(alpha = 0.18f)) else null,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    session.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            },
            supportingContent = {
                Text(
                    if (pending) {
                        "Respuesta en proceso · puedes volver después"
                    } else {
                        "${session.model.label} · ${session.messages.size} mensajes"
                    },
                    maxLines = 1,
                    fontSize = 11.sp,
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
                    tint = if (pending || session.isPinned || selected) NexoraAccent else NexoraMuted,
                )
            },
            trailingContent = {
                Row {
                    IconButton(onClick = { onTogglePin(session) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Fijar o soltar chat",
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    IconButton(onClick = { onDelete(session) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Eliminar chat",
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun ProjectRow(
    project: ChatProject,
    chatCount: Int,
    onNewChat: () -> Unit,
    onDelete: (ChatProject) -> Unit,
    onTogglePin: (ChatProject) -> Unit,
) {
    Surface(
        color = Color.White.copy(alpha = 0.025f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
    ) {
        ListItem(
            headlineContent = {
                Text(project.name, maxLines = 1, fontWeight = FontWeight.Bold)
            },
            supportingContent = { Text("$chatCount chats", fontSize = 11.sp) },
            leadingContent = {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (project.isPinned) NexoraAccent else NexoraBlue,
                )
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onNewChat, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Nuevo chat", modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = { onTogglePin(project) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PushPin, contentDescription = "Fijar proyecto", modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = { onDelete(project) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar proyecto", modifier = Modifier.size(17.dp))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
internal fun EmptyChatState(
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NexoraMark(76.dp)
        Text(
            "¿Qué quieres crear hoy?",
            fontSize = 27.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Text(
            "Conversa, programa, revisa código o coordina varios agentes desde el mismo espacio.",
            color = NexoraMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        listOf(
            "Convierte mi idea en un plan técnico",
            "Revisa este código y encuentra errores",
            "Diseña una aplicación Android moderna",
            "Explícame este tema paso a paso",
        ).forEach { suggestion ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSuggestion(suggestion) },
                color = Color.White.copy(alpha = 0.035f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(NexoraAccent.copy(alpha = 0.11f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NexoraAccent,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(suggestion, color = Color(0xFFE8EFED), fontSize = 13.sp)
                }
            }
        }
        Surface(
            color = NexoraAccent.copy(alpha = 0.07f),
            shape = RoundedCornerShape(17.dp),
            border = BorderStroke(1.dp, NexoraAccent.copy(alpha = 0.16f)),
        ) {
            Text(
                "Nexora muestra progreso operativo y tiempos, no razonamiento privado del modelo.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = Color(0xFFC7F9E9),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
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
    val context = LocalContext.current
    val isUser = message.role == "user"
    var detailsVisible by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 1f),
            color = if (isUser) Color(0xFF242932) else Color.Transparent,
            shape = RoundedCornerShape(if (isUser) 23.dp else 16.dp),
            border = if (isUser) {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
            } else {
                null
            },
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (isUser) 16.dp else 4.dp,
                    vertical = 13.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                MessageHeader(isUser = isUser)

                message.attachmentNames.forEach { name ->
                    Surface(
                        color = Color.White.copy(alpha = 0.035f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = NexoraMuted,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                name,
                                color = Color(0xFFD5E5E0),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                RichMessageContent(message.content)

                if (!isUser) {
                    val artifact = message.buildArtifact
                    if (artifact != null) {
                        AndroidBuildCard(artifact = artifact, onDownload = onDownload)
                    } else if (
                        userBuildsEnabled &&
                        message.content.isNotBlank() &&
                        message.requestStatus != RequestStatus.FAILED
                    ) {
                        FilledTonalButton(
                            onClick = { onBuild(message) },
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(7.dp))
                            Text("Crear APK con esta respuesta")
                        }
                    }
                }

                if (message.content.isNotBlank()) {
                    MessageActionRow(
                        isUser = isUser,
                        onCopy = { copyText(context, message.content) },
                        onShare = { shareText(context, message.content) },
                    )
                }

                if (!isUser && message.elapsedMs != null) {
                    HorizontalDivider(color = NexoraDivider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detailsVisible = !detailsVisible },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${formatDuration(message.elapsedMs)} · ${message.agentsUsed ?: 0} agente(s)",
                            modifier = Modifier.weight(1f),
                            color = NexoraMuted,
                            fontSize = 11.sp,
                        )
                        Icon(
                            if (detailsVisible) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Mostrar actividad",
                            tint = NexoraMuted,
                            modifier = Modifier.size(19.dp),
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
private fun MessageHeader(isUser: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .background(
                    if (isUser) Color.White.copy(alpha = 0.08f) else NexoraAccent.copy(alpha = 0.12f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isUser) "T" else "N",
                color = if (isUser) Color.White else NexoraAccent,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            if (isUser) "Tú" else "Nexora AI",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (isUser) Color(0xFFF2F4F7) else Color(0xFFD8FFF4),
        )
    }
}

@Composable
private fun RichMessageContent(content: String) {
    if (content.isBlank()) return
    val parts = remember(content) { content.split("```") }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        parts.forEachIndexed { index, raw ->
            if (raw.isBlank()) return@forEachIndexed
            if (index % 2 == 0) {
                SelectionContainer {
                    Text(
                        raw.trim(),
                        color = Color(0xFFEAF0EE),
                        lineHeight = 22.sp,
                        fontSize = 14.sp,
                    )
                }
            } else {
                CodeBlock(raw)
            }
        }
    }
}

@Composable
private fun CodeBlock(raw: String) {
    val context = LocalContext.current
    val lines = raw.trim('\n').lines()
    val first = lines.firstOrNull().orEmpty().trim()
    val hasLanguage = first.isNotBlank() &&
        first.length <= 20 &&
        !first.contains(' ') &&
        first.matches(Regex("[A-Za-z0-9_+.#-]+"))
    val language = if (hasLanguage) first else "código"
    val code = if (hasLanguage) lines.drop(1).joinToString("\n") else lines.joinToString("\n")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0B0E13),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.025f))
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Code, contentDescription = null, tint = NexoraAccent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    language,
                    modifier = Modifier.weight(1f),
                    color = NexoraMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
                IconButton(onClick = { copyText(context, code) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar código", modifier = Modifier.size(15.dp))
                }
            }
            SelectionContainer {
                Text(
                    code,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFD8E3E0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun MessageActionRow(
    isUser: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        TextButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(5.dp))
            Text("Copiar", fontSize = 11.sp)
        }
        TextButton(onClick = onShare, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(5.dp))
            Text("Compartir", fontSize = 11.sp)
        }
    }
}

@Composable
private fun AndroidBuildCard(
    artifact: AndroidBuildArtifact,
    onDownload: (String) -> Unit,
) {
    val completed = artifact.status == AndroidBuildStatus.COMPLETED
    val failed = artifact.status == AndroidBuildStatus.FAILED || artifact.status == AndroidBuildStatus.EXPIRED
    Surface(
        color = when {
            completed -> NexoraAccent.copy(alpha = 0.09f)
            failed -> Color(0xFFEF4444).copy(alpha = 0.09f)
            else -> Color.White.copy(alpha = 0.035f)
        },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            when {
                completed -> NexoraAccent.copy(alpha = 0.24f)
                failed -> Color(0xFFEF4444).copy(alpha = 0.25f)
                else -> Color.White.copy(alpha = 0.08f)
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
                        else -> NexoraBlue
                    },
                )
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(artifact.appName, fontWeight = FontWeight.Bold)
                    Text(artifact.progressLabel, color = NexoraMuted, fontSize = 11.sp)
                }
            }
            if (!completed && !failed) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (completed) {
                Text(
                    "Firma ${artifact.signatureSchemes.joinToString(" + ")} · enlace temporal de 1 hora",
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
    Column(
        modifier = Modifier.padding(top = 7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
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
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(step.label, color = Color(0xFFC6D2D0), fontSize = 11.sp)
                }
            }
        validation?.let {
            Surface(
                color = NexoraViolet.copy(alpha = 0.07f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NexoraViolet.copy(alpha = 0.14f)),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = NexoraViolet)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Laboratorio: ${it.status}${it.language?.let { language -> " · $language" } ?: ""}",
                        color = Color(0xFFD9E6E2),
                        fontSize = 11.sp,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NexoraSurface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, NexoraAccent.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(NexoraAccent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.RadioButtonChecked,
                        contentDescription = null,
                        tint = NexoraAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Nexora AI está trabajando", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(active?.label ?: label, color = NexoraMuted, fontSize = 11.sp)
                }
                Text(formatDuration(elapsedMs), color = NexoraAccent, fontWeight = FontWeight.Black, fontSize = 12.sp)
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
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(7.dp))
                        Text(step.label, color = Color(0xFFC6D2D0), fontSize = 10.sp)
                    }
                }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Nexora AI", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir respuesta"))
}

internal fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000.0
    return if (seconds < 60) {
        String.format("%.1f s", seconds)
    } else {
        val minutes = (seconds / 60).toInt()
        val remaining = (seconds % 60).toInt()
        "${minutes}m ${remaining}s"
    }
}

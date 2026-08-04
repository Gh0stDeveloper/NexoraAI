package com.ghostnexora.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NexoraBackground = Color(0xFF06080E)
private val NexoraSurface = Color(0xFF101722)
private val NexoraAccent = Color(0xFF38E8B0)
private val NexoraMuted = Color(0xFF94A3B8)

@Composable
fun NexoraRoot() {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    if (showSplash) {
        NexoraSplashScreen(onFinished = { showSplash = false })
    } else {
        NexoraChatApp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NexoraChatApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ChatStore(context.applicationContext) }
    val sessions = remember {
        mutableStateListOf<ChatSession>().apply {
            addAll(store.load())
            if (isEmpty()) add(ChatSession())
        }
    }
    var activeSessionId by rememberSaveable { mutableStateOf(sessions.first().id) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val attachments = remember { mutableStateListOf<PendingAttachment>() }
    var prompt by rememberSaveable { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(sessions.first().model) }
    var intelligence by remember { mutableStateOf(sessions.first().intelligence) }
    var loading by remember { mutableStateOf(false) }
    var plusMenuExpanded by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showIntelligenceDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    fun activeIndex(): Int = sessions.indexOfFirst { it.id == activeSessionId }

    fun persistCurrent() {
        val index = activeIndex()
        if (index < 0) return
        val current = sessions[index]
        val firstUserMessage = messages.firstOrNull { it.role == "user" }?.content
        sessions[index] = current.copy(
            title = firstUserMessage?.lineSequence()?.firstOrNull()?.take(42)?.ifBlank { "Nuevo chat" }
                ?: current.title,
            updatedAt = System.currentTimeMillis(),
            model = selectedModel,
            intelligence = intelligence,
            messages = messages.toList(),
        )
        store.save(sessions)
    }

    fun selectSession(session: ChatSession) {
        persistCurrent()
        activeSessionId = session.id
        selectedModel = session.model
        intelligence = session.intelligence
        messages.clear()
        messages.addAll(session.messages)
        attachments.clear()
        prompt = ""
        scope.launch { drawerState.close() }
    }

    fun createNewChat() {
        persistCurrent()
        val session = ChatSession()
        sessions.add(0, session)
        activeSessionId = session.id
        selectedModel = NexoraModel.AUTO
        intelligence = IntelligenceLevel.MEDIUM
        messages.clear()
        attachments.clear()
        prompt = ""
        store.save(sessions)
        scope.launch { drawerState.close() }
    }

    fun deleteSession(session: ChatSession) {
        val wasActive = session.id == activeSessionId
        sessions.removeAll { it.id == session.id }
        if (sessions.isEmpty()) sessions.add(ChatSession())
        if (wasActive) {
            val replacement = sessions.first()
            activeSessionId = replacement.id
            selectedModel = replacement.model
            intelligence = replacement.intelligence
            messages.clear()
            messages.addAll(replacement.messages)
        }
        store.save(sessions)
    }

    LaunchedEffect(activeSessionId) {
        val session = sessions.firstOrNull { it.id == activeSessionId } ?: return@LaunchedEffect
        messages.clear()
        messages.addAll(session.messages)
    }

    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty() || loading) {
            listState.animateScrollToItem((messages.size + if (loading) 1 else 0).coerceAtLeast(1) - 1)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val attachment = withContext(Dispatchers.IO) {
                        AttachmentReader.read(context, it, context.contentResolver.getType(it))
                    }
                    if (attachments.size < 3) attachments.add(attachment)
                    else snackbarHostState.showSnackbar("Puedes adjuntar hasta 3 elementos por mensaje.")
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(error.message ?: "No se pudo leer la imagen.")
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val attachment = withContext(Dispatchers.IO) { AttachmentReader.read(context, it) }
                    if (attachments.size < 3) attachments.add(attachment)
                    else snackbarHostState.showSnackbar("Puedes adjuntar hasta 3 elementos por mensaje.")
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(error.message ?: "No se pudo leer el archivo.")
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = NexoraAccent,
            background = NexoraBackground,
            surface = NexoraSurface,
            onSurface = Color(0xFFF8FAFC),
        ),
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Color(0xFF0B111B),
                    modifier = Modifier.fillMaxWidth(0.86f),
                ) {
                    DrawerContent(
                        sessions = sessions.sortedByDescending { it.updatedAt },
                        activeSessionId = activeSessionId,
                        onNewChat = { createNewChat() },
                        onSelect = { selectSession(it) },
                        onDelete = { deleteSession(it) },
                    )
                }
            },
        ) {
            Scaffold(
                containerColor = NexoraBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF080C13)),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Abrir historial")
                            }
                        },
                        title = {
                            Column {
                                Text("Nexora AI", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                Text(
                                    "${selectedModel.label} · ${intelligence.label}",
                                    color = NexoraMuted,
                                    fontSize = 12.sp,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { createNewChat() }) {
                                Icon(Icons.Default.Add, contentDescription = "Nuevo chat")
                            }
                        },
                    )
                },
                bottomBar = {
                    MessageComposer(
                        prompt = prompt,
                        onPromptChange = { prompt = it },
                        attachments = attachments,
                        loading = loading,
                        plusMenuExpanded = plusMenuExpanded,
                        onPlusMenuChange = { plusMenuExpanded = it },
                        onImage = {
                            plusMenuExpanded = false
                            imagePicker.launch("image/*")
                        },
                        onFile = {
                            plusMenuExpanded = false
                            filePicker.launch(
                                arrayOf(
                                    "text/*",
                                    "application/pdf",
                                    "application/json",
                                    "application/xml",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        onModel = {
                            plusMenuExpanded = false
                            showModelDialog = true
                        },
                        onIntelligence = {
                            plusMenuExpanded = false
                            showIntelligenceDialog = true
                        },
                        onRemoveAttachment = { attachment -> attachments.remove(attachment) },
                        onSend = {
                            val text = prompt.trim().ifBlank { "Analiza el contenido adjunto y responde con claridad." }
                            val sentAttachments = attachments.toList()
                            val userMessage = ChatMessage(
                                role = "user",
                                content = text,
                                attachmentNames = sentAttachments.map { it.name },
                            )
                            messages.add(userMessage)
                            prompt = ""
                            attachments.clear()
                            loading = true
                            persistCurrent()
                            scope.launch {
                                val answer = withContext(Dispatchers.IO) {
                                    ApiClient.postChat(
                                        message = text,
                                        model = selectedModel,
                                        intelligence = intelligence,
                                        attachments = sentAttachments,
                                        conversationId = activeSessionId,
                                    )
                                }
                                messages.add(ChatMessage(role = "assistant", content = answer))
                                loading = false
                                persistCurrent()
                            }
                        },
                    )
                },
            ) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF07120F), NexoraBackground, Color(0xFF090D17)),
                            ),
                        ),
                ) {
                    if (messages.isEmpty() && !loading) {
                        EmptyChatState(
                            model = selectedModel,
                            intelligence = intelligence,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(messages, key = { it.id }) { message -> MessageBubble(message) }
                            if (loading) {
                                item {
                                    AssistantThinking(
                                        label = "${selectedModel.label} · ${intelligence.label}",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModelDialog) {
        ModelDialog(
            selected = selectedModel,
            onDismiss = { showModelDialog = false },
            onSelected = {
                selectedModel = it
                showModelDialog = false
                persistCurrent()
            },
        )
    }

    if (showIntelligenceDialog) {
        IntelligenceDialog(
            selected = intelligence,
            onDismiss = { showIntelligenceDialog = false },
            onSelected = {
                intelligence = it
                showIntelligenceDialog = false
                persistCurrent()
            },
        )
    }
}

@Composable
private fun DrawerContent(
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
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(NexoraAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("NX", color = Color(0xFF03130E), fontWeight = FontWeight.Black)
            }
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
        Divider(modifier = Modifier.padding(vertical = 14.dp), color = Color.White.copy(alpha = 0.08f))
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
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        containerColor = if (selected) NexoraAccent.copy(alpha = 0.12f) else Color.Transparent,
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
private fun EmptyChatState(
    model: NexoraModel,
    intelligence: IntelligenceLevel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    Brush.linearGradient(listOf(NexoraAccent, Color(0xFF17A77D))),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF03130E), modifier = Modifier.size(34.dp))
        }
        Text("¿Qué quieres construir hoy?", fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(
            "Adjunta imágenes, archivos o cambia el modelo desde el botón +.",
            color = NexoraMuted,
            fontSize = 15.sp,
        )
        AssistChip(
            onClick = {},
            label = { Text("${model.label} · ${intelligence.label}") },
            leadingIcon = { Icon(Icons.Default.ModelTraining, contentDescription = null) },
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isUser) "Tú" else "Nexora AI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (message.attachmentNames.isNotEmpty()) {
                    message.attachmentNames.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(name, color = Color(0xFFD5E5E0), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                Text(message.content, color = Color(0xFFEAF0EE), lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun AssistantThinking(label: String) {
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

@Composable
private fun MessageComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    attachments: List<PendingAttachment>,
    loading: Boolean,
    plusMenuExpanded: Boolean,
    onPlusMenuChange: (Boolean) -> Unit,
    onImage: () -> Unit,
    onFile: () -> Unit,
    onModel: () -> Unit,
    onIntelligence: () -> Unit,
    onRemoveAttachment: (PendingAttachment) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        color = Color(0xFF080C13),
        tonalElevation = 8.dp,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(attachments.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.height((attachments.size.coerceAtMost(3) * 42).dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(attachments, key = { it.id }) { attachment ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingIcon = {
                                Icon(
                                    if (attachment.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.AttachFile,
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar adjunto",
                                    modifier = Modifier.clickable { onRemoveAttachment(attachment) },
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF121C28)),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    FilledIconButton(
                        onClick = { onPlusMenuChange(true) },
                        enabled = !loading,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Adjuntar o cambiar modelo")
                    }
                    DropdownMenu(
                        expanded = plusMenuExpanded,
                        onDismissRequest = { onPlusMenuChange(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Imagen") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = onImage,
                        )
                        DropdownMenuItem(
                            text = { Text("Archivo") },
                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                            onClick = onFile,
                        )
                        DropdownMenuItem(
                            text = { Text("Modelo") },
                            leadingIcon = { Icon(Icons.Default.ModelTraining, contentDescription = null) },
                            onClick = onModel,
                        )
                        DropdownMenuItem(
                            text = { Text("Inteligencia") },
                            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                            onClick = onIntelligence,
                        )
                    }
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { Text("Mensaje para Nexora AI") },
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = !loading && (prompt.isNotBlank() || attachments.isNotEmpty()),
                    modifier = Modifier.size(48.dp),
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDialog(
    selected: NexoraModel,
    onDismiss: () -> Unit,
    onSelected: (NexoraModel) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar modelo") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(NexoraModel.entries) { model ->
                    ListItem(
                        headlineContent = { Text(model.label, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(model.description) },
                        leadingContent = { Icon(Icons.Default.ModelTraining, contentDescription = null) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = if (model == selected) NexoraAccent.copy(alpha = 0.12f) else Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(model) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun IntelligenceDialog(
    selected: IntelligenceLevel,
    onDismiss: () -> Unit,
    onSelected: (IntelligenceLevel) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nivel de inteligencia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IntelligenceLevel.entries.forEach { level ->
                    ListItem(
                        headlineContent = { Text(level.label, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(level.description) },
                        leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = if (level == selected) NexoraAccent.copy(alpha = 0.12f) else Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(level) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

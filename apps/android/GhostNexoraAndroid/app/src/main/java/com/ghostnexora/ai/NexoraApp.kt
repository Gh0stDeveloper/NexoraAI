package com.ghostnexora.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NexoraRoot() {
    NexoraChatApp()
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
    var composerPanel by remember { mutableStateOf<ComposerPanel?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    fun persistCurrent() {
        val index = sessions.indexOfFirst { it.id == activeSessionId }
        if (index < 0) return
        val current = sessions[index]
        val firstUserMessage = messages.firstOrNull { it.role == "user" }?.content
        sessions[index] = current.copy(
            title = firstUserMessage
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(42)
                ?.ifBlank { "Nuevo chat" }
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
        composerPanel = null
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
        composerPanel = null
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
        val session = sessions.firstOrNull { it.id == activeSessionId }
            ?: return@LaunchedEffect
        messages.clear()
        messages.addAll(session.messages)
    }

    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty() || loading) {
            listState.animateScrollToItem(
                (messages.size + if (loading) 1 else 0).coerceAtLeast(1) - 1,
            )
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val attachment = withContext(Dispatchers.IO) {
                        AttachmentReader.read(
                            context,
                            it,
                            context.contentResolver.getType(it),
                        )
                    }
                    if (attachments.size < 3) {
                        attachments.add(attachment)
                    } else {
                        snackbarHostState.showSnackbar(
                            "Puedes adjuntar hasta 3 elementos por mensaje.",
                        )
                    }
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(
                        error.message ?: "No se pudo leer la imagen.",
                    )
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val attachment = withContext(Dispatchers.IO) {
                        AttachmentReader.read(context, it)
                    }
                    if (attachments.size < 3) {
                        attachments.add(attachment)
                    } else {
                        snackbarHostState.showSnackbar(
                            "Puedes adjuntar hasta 3 elementos por mensaje.",
                        )
                    }
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(
                        error.message ?: "No se pudo leer el archivo.",
                    )
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
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
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
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF080C13),
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Abrir historial",
                                )
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    "Nexora AI",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                )
                                if (messages.isNotEmpty() || loading) {
                                    Text(
                                        selectedModel.label,
                                        color = NexoraMuted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { createNewChat() }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Nuevo chat",
                                )
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
                        panel = composerPanel,
                        onPanelChange = { composerPanel = it },
                        selectedModel = selectedModel,
                        intelligence = intelligence,
                        onImage = {
                            composerPanel = null
                            imagePicker.launch("image/*")
                        },
                        onFile = {
                            composerPanel = null
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
                        onModelSelected = {
                            selectedModel = it
                            persistCurrent()
                        },
                        onIntelligenceSelected = {
                            intelligence = it
                            persistCurrent()
                        },
                        onRemoveAttachment = { attachments.remove(it) },
                        onSend = {
                            val text = prompt.trim().ifBlank {
                                "Analiza el contenido adjunto y responde con claridad."
                            }
                            val sentAttachments = attachments.toList()
                            messages.add(
                                ChatMessage(
                                    role = "user",
                                    content = text,
                                    attachmentNames = sentAttachments.map { it.name },
                                ),
                            )
                            prompt = ""
                            attachments.clear()
                            composerPanel = null
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
                                messages.add(
                                    ChatMessage(
                                        role = "assistant",
                                        content = answer,
                                    ),
                                )
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
                                listOf(
                                    Color(0xFF07120F),
                                    NexoraBackground,
                                    Color(0xFF090D17),
                                ),
                            ),
                        ),
                ) {
                    if (messages.isEmpty() && !loading) {
                        EmptyChatState(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = 16.dp,
                                vertical = 18.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(messages, key = { it.id }) {
                                MessageBubble(it)
                            }
                            if (loading) {
                                item {
                                    AssistantThinking(
                                        label = if (intelligence.agentCount == 1) {
                                            "Procesando la solicitud"
                                        } else {
                                            "Coordinando ${intelligence.agentCount} agentes"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

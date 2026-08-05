package com.ghostnexora.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay
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
    val projects = remember { mutableStateListOf<ChatProject>().apply { addAll(store.loadProjects()) } }
    val sessions = remember {
        mutableStateListOf<ChatSession>().apply {
            addAll(store.loadSessions())
            if (isEmpty()) add(ChatSession())
        }
    }
    var activeSessionId by rememberSaveable { mutableStateOf(sessions.first().id) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val attachments = remember { mutableStateListOf<PendingAttachment>() }
    val thinkingProgress = remember { mutableStateListOf<AgentProgress>() }
    var prompt by rememberSaveable { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(sessions.first().model) }
    var intelligence by remember { mutableStateOf(sessions.first().intelligence) }
    var validateCode by remember { mutableStateOf(sessions.first().validateCode) }
    var loading by remember { mutableStateOf(false) }
    var requestStartedAt by remember { mutableLongStateOf(0L) }
    var thinkingElapsedMs by remember { mutableLongStateOf(0L) }
    var composerPanel by remember { mutableStateOf<ComposerPanel?>(null) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var projectDialogVisible by remember { mutableStateOf(false) }
    var projectName by rememberSaveable { mutableStateOf("") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    fun saveAll() = store.save(sessions, projects)

    fun persistCurrent() {
        val index = sessions.indexOfFirst { it.id == activeSessionId }
        if (index < 0) return
        val current = sessions[index]
        val firstUserMessage = messages.firstOrNull { it.role == "user" }?.content
        sessions[index] = current.copy(
            title = firstUserMessage
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(52)
                ?.ifBlank { "Nuevo chat" }
                ?: current.title,
            updatedAt = System.currentTimeMillis(),
            model = selectedModel,
            intelligence = intelligence,
            validateCode = validateCode,
            messages = messages.toList(),
        )
        saveAll()
    }

    fun selectSession(session: ChatSession) {
        if (loading) return
        persistCurrent()
        activeSessionId = session.id
        selectedModel = session.model
        intelligence = session.intelligence
        validateCode = session.validateCode
        messages.clear()
        messages.addAll(session.messages)
        attachments.clear()
        thinkingProgress.clear()
        prompt = ""
        composerPanel = null
        scope.launch { drawerState.close() }
    }

    fun createNewChat(projectId: String?) {
        if (loading) return
        persistCurrent()
        val session = ChatSession(projectId = projectId)
        sessions.add(0, session)
        activeSessionId = session.id
        selectedModel = NexoraModel.AUTO
        intelligence = IntelligenceLevel.MEDIUM
        validateCode = false
        messages.clear()
        attachments.clear()
        thinkingProgress.clear()
        prompt = ""
        composerPanel = null
        saveAll()
        scope.launch { drawerState.close() }
    }

    fun createProject(name: String) {
        val cleanName = name.trim().take(60)
        if (cleanName.isBlank()) return
        val project = ChatProject(name = cleanName)
        projects.add(0, project)
        projectName = ""
        projectDialogVisible = false
        saveAll()
        createNewChat(project.id)
    }

    fun deleteSession(session: ChatSession) {
        if (loading && session.id == activeSessionId) return
        val wasActive = session.id == activeSessionId
        sessions.removeAll { it.id == session.id }
        if (sessions.isEmpty()) sessions.add(ChatSession())
        if (wasActive) selectSession(sessions.first())
        saveAll()
    }

    fun deleteProject(project: ChatProject) {
        if (loading) return
        projects.removeAll { it.id == project.id }
        sessions.indices.forEach { index ->
            if (sessions[index].projectId == project.id) {
                sessions[index] = sessions[index].copy(projectId = null)
            }
        }
        saveAll()
    }

    fun toggleSessionPin(session: ChatSession) {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) sessions[index] = session.copy(isPinned = !session.isPinned)
        saveAll()
    }

    fun toggleProjectPin(project: ChatProject) {
        val index = projects.indexOfFirst { it.id == project.id }
        if (index >= 0) {
            projects[index] = project.copy(
                isPinned = !project.isPinned,
                updatedAt = System.currentTimeMillis(),
            )
        }
        saveAll()
    }

    LaunchedEffect(activeSessionId) {
        val session = sessions.firstOrNull { it.id == activeSessionId } ?: return@LaunchedEffect
        messages.clear()
        messages.addAll(session.messages)
    }

    LaunchedEffect(messages.size, loading, thinkingProgress.size) {
        if (messages.isNotEmpty() || loading) {
            listState.animateScrollToItem(
                (messages.size + if (loading) 1 else 0).coerceAtLeast(1) - 1,
            )
        }
    }

    LaunchedEffect(loading, requestStartedAt) {
        while (loading) {
            thinkingElapsedMs = System.currentTimeMillis() - requestStartedAt
            delay(100)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val attachment = withContext(Dispatchers.IO) {
                        AttachmentReader.read(context, it, context.contentResolver.getType(it))
                    }
                    if (attachments.size < 3) attachments.add(attachment) else {
                        snackbarHostState.showSnackbar("Puedes adjuntar hasta 3 elementos por mensaje.")
                    }
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(error.message ?: "No se pudo leer la imagen.")
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
                    if (attachments.size < 3) attachments.add(attachment) else {
                        snackbarHostState.showSnackbar("Puedes adjuntar hasta 3 elementos por mensaje.")
                    }
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
        BoxWithConstraints {
            val drawerFraction = if (maxWidth >= 600.dp) 0.58f else 0.9f
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !loading,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = Color(0xFF0B111B),
                        modifier = Modifier
                            .fillMaxWidth(drawerFraction)
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        DrawerContent(
                            sessions = sessions,
                            projects = projects,
                            activeSessionId = activeSessionId,
                            onNewChat = { createNewChat(it) },
                            onNewProject = { projectDialogVisible = true },
                            onSelect = { selectSession(it) },
                            onDelete = { deleteSession(it) },
                            onTogglePinSession = { toggleSessionPin(it) },
                            onDeleteProject = { deleteProject(it) },
                            onTogglePinProject = { toggleProjectPin(it) },
                            onOpenTerms = { legalDocument = LegalDocument.TERMS },
                            onOpenPrivacy = { legalDocument = LegalDocument.PRIVACY },
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
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Abrir historial")
                                }
                            },
                            title = {
                                val activeProject = sessions
                                    .firstOrNull { it.id == activeSessionId }
                                    ?.projectId
                                    ?.let { id -> projects.firstOrNull { it.id == id } }
                                Column {
                                    Text("Nexora AI", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                    Text(
                                        activeProject?.name ?: selectedModel.label,
                                        color = NexoraMuted,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                    )
                                }
                            },
                            actions = {
                                val active = sessions.firstOrNull { it.id == activeSessionId }
                                IconButton(
                                    onClick = { active?.let(::toggleSessionPin) },
                                    enabled = active != null,
                                ) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = "Fijar chat",
                                        tint = if (active?.isPinned == true) NexoraAccent else Color.White,
                                    )
                                }
                                IconButton(onClick = { createNewChat(active?.projectId) }, enabled = !loading) {
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
                            panel = composerPanel,
                            onPanelChange = { composerPanel = it },
                            selectedModel = selectedModel,
                            intelligence = intelligence,
                            validateCode = validateCode,
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
                            onValidateCodeChange = {
                                validateCode = it
                                persistCurrent()
                            },
                            onRemoveAttachment = { attachments.remove(it) },
                            onSend = {
                                val text = prompt.trim().ifBlank {
                                    "Analiza el contenido adjunto y responde con claridad."
                                }
                                val sentAttachments = attachments.toList()
                                val requestModel = selectedModel
                                val requestIntelligence = intelligence
                                val requestValidation = validateCode
                                val session = sessions.firstOrNull { it.id == activeSessionId }
                                messages.add(
                                    ChatMessage(
                                        role = "user",
                                        content = text,
                                        attachmentNames = sentAttachments.map { it.name },
                                    ),
                                )
                                prompt = ""
                                attachments.clear()
                                thinkingProgress.clear()
                                composerPanel = null
                                loading = true
                                requestStartedAt = System.currentTimeMillis()
                                thinkingElapsedMs = 0L
                                persistCurrent()
                                scope.launch {
                                    val response = withContext(Dispatchers.IO) {
                                        ApiClient.postChat(
                                            message = text,
                                            model = requestModel,
                                            intelligence = requestIntelligence,
                                            attachments = sentAttachments,
                                            conversationId = activeSessionId,
                                            projectId = session?.projectId,
                                            validateCode = requestValidation,
                                            onProgress = { progress ->
                                                scope.launch { thinkingProgress.add(progress) }
                                            },
                                        )
                                    }
                                    messages.add(
                                        ChatMessage(
                                            role = "assistant",
                                            content = response.answer,
                                            elapsedMs = response.elapsedMs.takeIf { it > 0 },
                                            agentsUsed = response.agentsUsed,
                                            provider = response.provider,
                                            trace = response.trace,
                                            codeValidation = response.codeValidation,
                                        ),
                                    )
                                    loading = false
                                    thinkingElapsedMs = response.elapsedMs
                                    thinkingProgress.clear()
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
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(messages, key = { it.id }) { MessageBubble(it) }
                                if (loading) {
                                    item {
                                        AssistantThinking(
                                            label = if (intelligence.agentCount == 1) {
                                                "Procesando la solicitud"
                                            } else {
                                                "Coordinando ${intelligence.agentCount} agentes"
                                            },
                                            elapsedMs = thinkingElapsedMs,
                                            progress = thinkingProgress,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (projectDialogVisible) {
            AlertDialog(
                onDismissRequest = { projectDialogVisible = false },
                title = { Text("Nuevo proyecto") },
                text = {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it.take(60) },
                        singleLine = true,
                        label = { Text("Nombre") },
                        placeholder = { Text("Ej. Aplicación Android") },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { createProject(projectName) },
                        enabled = projectName.isNotBlank(),
                    ) { Text("Crear") }
                },
                dismissButton = {
                    TextButton(onClick = { projectDialogVisible = false }) { Text("Cancelar") }
                },
            )
        }

        legalDocument?.let { document ->
            LegalDocumentDialog(document = document, onDismiss = { legalDocument = null })
        }
    }
}

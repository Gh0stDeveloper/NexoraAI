package com.ghostnexora.ai

import android.content.Intent
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import java.util.UUID

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
    val pendingWork = remember { PendingWorkStore(context.applicationContext) }
    val projects = remember { mutableStateListOf<ChatProject>().apply { addAll(store.loadProjects()) } }
    val sessions = remember {
        mutableStateListOf<ChatSession>().apply {
            addAll(store.loadSessions())
            if (isEmpty()) add(ChatSession(model = NexoraModel.ASSISTANT))
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
    var historyRevision by remember { mutableIntStateOf(1) }
    var buildMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var buildAppName by rememberSaveable { mutableStateOf("") }
    var userBuildsEnabled by remember { mutableStateOf(false) }
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
        persistCurrent()
        activeSessionId = session.id
        selectedModel = session.model
        intelligence = session.intelligence
        validateCode = session.validateCode
        messages.clear()
        messages.addAll(session.messages)
        attachments.clear()
        thinkingProgress.clear()
        thinkingProgress.addAll(
            session.messages.lastOrNull {
                it.requestStatus == RequestStatus.QUEUED ||
                    it.requestStatus == RequestStatus.PROCESSING
            }?.trace.orEmpty(),
        )
        loading = session.messages.any {
            it.requestStatus == RequestStatus.QUEUED ||
                it.requestStatus == RequestStatus.PROCESSING
        }
        requestStartedAt = session.messages.lastOrNull { it.requestId != null }?.createdAt ?: 0L
        prompt = ""
        composerPanel = null
        scope.launch { drawerState.close() }
    }

    fun createNewChat(projectId: String?) {
        persistCurrent()
        val session = ChatSession(projectId = projectId, model = NexoraModel.ASSISTANT)
        sessions.add(0, session)
        activeSessionId = session.id
        selectedModel = NexoraModel.ASSISTANT
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
        if (sessions.isEmpty()) sessions.add(ChatSession(model = NexoraModel.ASSISTANT))
        if (wasActive) selectSession(sessions.first())
        saveAll()
    }

    fun deleteProject(project: ChatProject) {
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

    DisposableEffect(store) {
        val observer = store.observe { historyRevision += 1 }
        onDispose { observer.close() }
    }

    LaunchedEffect(Unit) {
        DurableWorkScheduler.resumeAll(context.applicationContext)
        val release = withContext(Dispatchers.IO) {
            runCatching { ApiClient.getLatestMobileRelease() }.getOrNull()
        }
        userBuildsEnabled = release?.userBuildsEnabled == true
        if (release != null && release.versionCode > BuildConfig.VERSION_CODE) {
            val result = snackbarHostState.showSnackbar(
                message = "Nexora AI ${release.version} está disponible",
                actionLabel = "Descargar",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl)))
            }
        }
    }

    LaunchedEffect(historyRevision) {
        val refreshedSessions = store.loadSessions()
        val refreshedProjects = store.loadProjects()
        sessions.clear()
        sessions.addAll(refreshedSessions.ifEmpty { listOf(ChatSession(model = NexoraModel.ASSISTANT)) })
        projects.clear()
        projects.addAll(refreshedProjects)
        val active = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.first()
        if (active.id != activeSessionId) activeSessionId = active.id
        messages.clear()
        messages.addAll(active.messages)
        selectedModel = active.model
        intelligence = active.intelligence
        validateCode = active.validateCode
        val pending = active.messages.lastOrNull {
            it.requestStatus == RequestStatus.QUEUED ||
                it.requestStatus == RequestStatus.PROCESSING
        }
        loading = pending != null
        thinkingProgress.clear()
        thinkingProgress.addAll(pending?.trace.orEmpty())
        requestStartedAt = pending?.createdAt ?: 0L
        if (!loading) {
            thinkingElapsedMs = active.messages.lastOrNull()?.elapsedMs ?: 0L
        }
    }

    LaunchedEffect(activeSessionId) {
        val session = sessions.firstOrNull { it.id == activeSessionId } ?: return@LaunchedEffect
        messages.clear()
        messages.addAll(session.messages)
    }

    LaunchedEffect(messages.size, loading, thinkingProgress.size) {
        val renderedMessages = messages.count {
            it.requestStatus != RequestStatus.QUEUED &&
                it.requestStatus != RequestStatus.PROCESSING
        } + if (loading) 1 else 0
        if (renderedMessages > 0) {
            listState.animateScrollToItem(renderedMessages - 1)
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
                gesturesEnabled = true,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = Color(0xFF111111),
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
                                containerColor = Color(0xFF0D0D0D),
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
                                IconButton(onClick = { createNewChat(active?.projectId) }) {
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
                                val conversationId = activeSessionId
                                val requestId = UUID.randomUUID().toString()
                                val requestToken = DurableWorkScheduler.newRequestToken()
                                val startedAt = System.currentTimeMillis()
                                messages.add(
                                    ChatMessage(
                                        role = "user",
                                        content = text,
                                        attachmentNames = sentAttachments.map { it.name },
                                    ),
                                )
                                messages.add(
                                    ChatMessage(
                                        id = "assistant-$requestId",
                                        role = "assistant",
                                        content = "",
                                        createdAt = startedAt,
                                        updatedAt = startedAt,
                                        requestId = requestId,
                                        requestStatus = RequestStatus.QUEUED,
                                    ),
                                )
                                prompt = ""
                                attachments.clear()
                                thinkingProgress.clear()
                                composerPanel = null
                                loading = true
                                requestStartedAt = startedAt
                                thinkingElapsedMs = 0L
                                persistCurrent()
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            pendingWork.saveChat(
                                                PendingChatRequest(
                                                    requestId = requestId,
                                                    requestToken = requestToken,
                                                    conversationId = conversationId,
                                                    projectId = session?.projectId,
                                                    message = text,
                                                    model = requestModel,
                                                    intelligence = requestIntelligence,
                                                    validateCode = requestValidation,
                                                    attachments = sentAttachments,
                                                ),
                                            )
                                        }
                                        DurableWorkScheduler.enqueueChat(
                                            context.applicationContext,
                                            requestId,
                                        )
                                    } catch (error: Exception) {
                                        val index = messages.indexOfFirst { it.requestId == requestId }
                                        if (index >= 0) {
                                            messages[index] = messages[index].copy(
                                                content = error.message
                                                    ?: "No se pudo guardar la solicitud pendiente.",
                                                updatedAt = System.currentTimeMillis(),
                                                requestStatus = RequestStatus.FAILED,
                                            )
                                        }
                                        loading = false
                                        persistCurrent()
                                    }
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
                                        Color(0xFF10201B),
                                        NexoraBackground,
                                        Color(0xFF0D0D0D),
                                    ),
                                ),
                            ),
                    ) {
                        if (messages.isEmpty() && !loading) {
                            EmptyChatState(
                                modifier = Modifier.align(Alignment.Center),
                                onSuggestion = { prompt = it },
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    messages.filterNot {
                                        it.requestStatus == RequestStatus.QUEUED ||
                                            it.requestStatus == RequestStatus.PROCESSING
                                    },
                                    key = { it.id },
                                ) { message ->
                                    MessageBubble(
                                        message = message,
                                        userBuildsEnabled = userBuildsEnabled,
                                        onBuild = { selected ->
                                            buildMessage = selected
                                            buildAppName = sessions
                                                .firstOrNull { it.id == activeSessionId }
                                                ?.title
                                                ?.takeUnless { it == "Nuevo chat" }
                                                ?: "Aplicación Nexora"
                                        },
                                        onDownload = { url ->
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                            )
                                        },
                                    )
                                }
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

        buildMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { buildMessage = null },
                title = { Text("Crear APK temporal") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Nexora compilará una aplicación nativa con esta respuesta. " +
                                "El APK y su enlace se eliminarán una hora después de quedar listos.",
                            color = NexoraMuted,
                            fontSize = 13.sp,
                        )
                        OutlinedTextField(
                            value = buildAppName,
                            onValueChange = { buildAppName = it.take(48) },
                            singleLine = true,
                            label = { Text("Nombre de la aplicación") },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = buildAppName.trim().length >= 2,
                        onClick = {
                            val cleanName = buildAppName.trim().take(48)
                            val conversationId = activeSessionId
                            val requestId = UUID.randomUUID().toString()
                            val requestToken = DurableWorkScheduler.newRequestToken()
                            val messageIndex = messages.indexOfFirst { it.id == message.id }
                            val sourcePrompt = messages
                                .take(messageIndex.coerceAtLeast(0))
                                .lastOrNull { it.role == "user" }
                                ?.content
                                ?: "Crea una aplicación basada en la respuesta."
                            val queuedArtifact = AndroidBuildArtifact(
                                requestId = requestId,
                                status = AndroidBuildStatus.QUEUED,
                                appName = cleanName,
                                progressLabel = "En cola para compilar",
                            )
                            if (messageIndex >= 0) {
                                messages[messageIndex] = messages[messageIndex].copy(
                                    updatedAt = System.currentTimeMillis(),
                                    buildArtifact = queuedArtifact,
                                )
                                persistCurrent()
                            }
                            buildMessage = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        pendingWork.saveBuild(
                                            PendingAndroidBuildRequest(
                                                requestId = requestId,
                                                requestToken = requestToken,
                                                deviceId = pendingWork.deviceId(),
                                                conversationId = conversationId,
                                                messageId = message.id,
                                                appName = cleanName,
                                                accentColor = "#10A37F",
                                                sourcePrompt = sourcePrompt,
                                                sourceContent = message.content,
                                            ),
                                        )
                                    }
                                    DurableWorkScheduler.enqueueBuild(
                                        context.applicationContext,
                                        requestId,
                                    )
                                } catch (error: Exception) {
                                    store.updateAndroidBuild(
                                        conversationId,
                                        message.id,
                                        queuedArtifact.copy(
                                            status = AndroidBuildStatus.FAILED,
                                            progressLabel = "No se pudo iniciar la compilación",
                                            error = error.message,
                                        ),
                                    )
                                }
                            }
                        },
                    ) { Text("Compilar") }
                },
                dismissButton = {
                    TextButton(onClick = { buildMessage = null }) { Text("Cancelar") }
                },
            )
        }

        legalDocument?.let { document ->
            LegalDocumentDialog(document = document, onDismiss = { legalDocument = null })
        }
    }
}

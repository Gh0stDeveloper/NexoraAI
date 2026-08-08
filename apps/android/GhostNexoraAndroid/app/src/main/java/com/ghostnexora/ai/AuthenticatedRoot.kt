package com.ghostnexora.ai

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoraAuthenticatedRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authStore = remember { AuthStore(context.applicationContext) }
    val cloudSync = remember { CloudChatSync(context.applicationContext) }
    var session by remember { mutableStateOf(authStore.loadSession()) }
    var loading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var accountSheetVisible by remember { mutableStateOf(false) }
    var pushJob by remember { mutableStateOf<Job?>(null) }
    val callback by AuthCallbackBus.callback.collectAsState()

    fun finishLogin(next: NexoraAuthSession) {
        authStore.saveSession(next)
        session = next
        authError = null
        scope.launch(Dispatchers.IO) {
            runCatching { cloudSync.sync(authStore) }
        }
    }

    LaunchedEffect(Unit) {
        val existing = authStore.loadSession() ?: return@LaunchedEffect
        loading = true
        val validated = withContext(Dispatchers.IO) {
            runCatching {
                val fresh = AuthApi.ensureFreshSession(authStore) ?: return@runCatching null
                val currentUser = AuthApi.getCurrentUser(authStore) ?: fresh.user
                fresh.copy(user = currentUser).also(authStore::saveSession)
            }.getOrNull()
        }
        session = validated
        if (validated == null) authStore.clearSession()
        loading = false
        if (validated != null) {
            withContext(Dispatchers.IO) {
                runCatching { cloudSync.sync(authStore) }
            }
        }
    }

    LaunchedEffect(callback) {
        val uri = callback ?: return@LaunchedEffect
        AuthCallbackBus.consume()
        val pending = authStore.loadPendingOAuth()
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            authStore.clearPendingOAuth()
            authError = error
            loading = false
            return@LaunchedEffect
        }
        val state = uri.getQueryParameter("state")
        val code = uri.getQueryParameter("code")
        if (
            pending == null ||
            state != pending.state ||
            code.isNullOrBlank() ||
            System.currentTimeMillis() - pending.startedAt > OAUTH_MAX_AGE_MS
        ) {
            authStore.clearPendingOAuth()
            authError = "La respuesta de inicio de sesión no es válida o expiró."
            loading = false
            return@LaunchedEffect
        }

        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching { AuthApi.exchangeOAuth(code, pending.verifier) }
        }
        authStore.clearPendingOAuth()
        result.onSuccess(::finishLogin).onFailure {
            authError = it.message ?: "No se pudo completar el inicio de sesión."
        }
        loading = false
    }

    DisposableEffect(session?.user?.id) {
        if (session == null) return@DisposableEffect onDispose { }
        val preferences = context.getSharedPreferences("nexora_chat_history", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sessions" || key == "projects") {
                pushJob?.cancel()
                pushJob = scope.launch {
                    delay(CLOUD_PUSH_DEBOUNCE_MS)
                    withContext(Dispatchers.IO) {
                        runCatching { cloudSync.push(authStore) }
                    }
                }
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            pushJob?.cancel()
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NexoraAccent,
            secondary = NexoraViolet,
            tertiary = NexoraBlue,
            background = NexoraBackground,
            surface = NexoraSurface,
            surfaceVariant = NexoraSurfaceElevated,
            onPrimary = Color(0xFF04130E),
            onBackground = Color(0xFFF6F7FB),
            onSurface = Color(0xFFF6F7FB),
        ),
    ) {
        val activeSession = session
        if (activeSession == null) {
            AuthScreen(
                loading = loading,
                error = authError,
                onSocial = { provider ->
                    authError = null
                    runCatching {
                        AuthApi.socialStart(provider, authStore)
                    }.onSuccess { uri ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }.onFailure {
                        authError = it.message ?: "No se pudo abrir el proveedor."
                    }
                },
                onEmail = { register, name, email, password ->
                    loading = true
                    authError = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                AuthApi.emailAuth(register, name, email, password)
                            }
                        }
                        result.onSuccess(::finishLogin).onFailure {
                            authError = it.message ?: "No se pudo iniciar sesión."
                        }
                        loading = false
                    }
                },
                onClearError = { authError = null },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                NexoraRoot()
                AccountFloatingChip(
                    user = activeSession.user,
                    onClick = { accountSheetVisible = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 14.dp, bottom = 92.dp),
                )
            }

            if (accountSheetVisible) {
                ModalBottomSheet(
                    onDismissRequest = { accountSheetVisible = false },
                    containerColor = NexoraSurface,
                ) {
                    AccountSheet(
                        user = activeSession.user,
                        onLogout = {
                            accountSheetVisible = false
                            loading = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { AuthApi.logout(authStore) }
                                }
                                session = null
                                loading = false
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountFloatingChip(
    user: NexoraUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = NexoraSurfaceElevated.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, NexoraAccent.copy(alpha = 0.35f)),
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .size(43.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            NexoraAccent.copy(alpha = 0.28f),
                            NexoraViolet.copy(alpha = 0.22f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user.name.trim().firstOrNull()?.uppercase() ?: "N",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun AccountSheet(
    user: NexoraUser,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Cuenta Nexora", fontSize = 21.sp, fontWeight = FontWeight.Black)
        Card(
            colors = CardDefaults.cardColors(containerColor = NexoraSurfaceElevated),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = NexoraAccent.copy(alpha = 0.16f),
                ) {
                    Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = NexoraAccent)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        user.email ?: "Cuenta social",
                        color = NexoraMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Surface(
            color = NexoraAccent.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, NexoraAccent.copy(alpha = 0.18f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = NexoraAccent)
                Column {
                    Text("Sincronización activa", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Chats y proyectos se respaldan en tu cuenta.",
                        color = NexoraMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Cerrar sesión", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(8.dp))
    }
}

private const val OAUTH_MAX_AGE_MS = 10 * 60 * 1_000L
private const val CLOUD_PUSH_DEBOUNCE_MS = 1_200L

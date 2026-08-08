package com.ghostnexora.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AccountCenter(
    authStore: AuthStore,
    initialUser: NexoraUser,
    onUserUpdated: (NexoraUser) -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var overview by remember { mutableStateOf<NexoraAccountOverview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var actionLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(initialUser.name) }
    var verificationRequested by rememberSaveable { mutableStateOf(false) }
    var verificationCode by rememberSaveable { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching { AuthApi.getAccountOverview(authStore) }
            }
            result.onSuccess {
                overview = it
                name = it.user.name
                onUserUpdated(it.user)
            }.onFailure {
                error = it.message ?: "No se pudo cargar la cuenta."
            }
            loading = false
        }
    }

    fun runAction(
        successMessage: String,
        operation: () -> Unit,
        refreshAfter: Boolean = true,
    ) {
        scope.launch {
            actionLoading = true
            error = null
            notice = null
            val result = withContext(Dispatchers.IO) { runCatching(operation) }
            result.onSuccess {
                notice = successMessage
                if (refreshAfter) {
                    val next = withContext(Dispatchers.IO) {
                        runCatching { AuthApi.getAccountOverview(authStore) }
                    }.getOrNull()
                    if (next != null) {
                        overview = next
                        name = next.user.name
                        onUserUpdated(next.user)
                    }
                }
            }.onFailure {
                error = it.message ?: "No se pudo completar la operación."
            }
            actionLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Centro de cuenta", fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(
                    "Perfil, seguridad y dispositivos",
                    color = NexoraMuted,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = ::refresh, enabled = !loading && !actionLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar cuenta")
            }
        }

        if (loading && overview == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text("Cargando cuenta…", color = NexoraMuted)
            }
        }

        val account = overview
        if (account != null) {
            ProfileCard(
                user = account.user,
                emailVerified = account.emailVerified,
                editingName = editingName,
                name = name,
                actionLoading = actionLoading,
                onNameChange = { name = it.take(80) },
                onEdit = { editingName = true },
                onCancelEdit = {
                    editingName = false
                    name = account.user.name
                },
                onSaveName = {
                    runAction("Nombre actualizado.", operation = {
                        val updated = AuthApi.updateAccountName(authStore, name)
                        val current = authStore.loadSession()
                        if (current != null) {
                            authStore.saveSession(current.copy(user = updated))
                        }
                    })
                    editingName = false
                },
            )

            if (account.user.email != null && !account.emailVerified) {
                EmailVerificationCard(
                    email = account.user.email,
                    code = verificationCode,
                    requested = verificationRequested,
                    loading = actionLoading,
                    onCodeChange = {
                        verificationCode = it.filter(Char::isDigit).take(6)
                    },
                    onRequest = {
                        runAction(
                            successMessage = "Código enviado. Revisa tu correo.",
                            refreshAfter = false,
                            operation = { AuthApi.requestEmailVerification(authStore) },
                        )
                        verificationRequested = true
                    },
                    onConfirm = {
                        runAction(
                            successMessage = "Correo verificado correctamente.",
                            operation = {
                                AuthApi.confirmEmailVerification(authStore, verificationCode)
                            },
                        )
                        verificationCode = ""
                    },
                )
            }

            ProvidersCard(
                providers = account.providers,
                hasPassword = account.hasPassword,
            )

            SessionsCard(
                sessions = account.sessions,
                loading = actionLoading,
                onRevoke = { sessionId ->
                    runAction(
                        successMessage = "Sesión cerrada.",
                        operation = { AuthApi.revokeSession(authStore, sessionId) },
                    )
                },
                onRevokeOthers = {
                    runAction(
                        successMessage = "Se cerraron las demás sesiones.",
                        operation = { AuthApi.revokeOtherSessions(authStore) },
                    )
                },
            )
        }

        notice?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NexoraAccent.copy(alpha = 0.09f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NexoraAccent.copy(alpha = 0.20f)),
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(13.dp),
                    color = Color(0xFFD5FFF3),
                    fontSize = 12.sp,
                )
            }
        }

        error?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFEF4444).copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.25f)),
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(13.dp),
                    color = Color(0xFFFECACA),
                    fontSize = 12.sp,
                )
            }
        }

        HorizontalDivider(color = NexoraDivider)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NexoraViolet.copy(alpha = 0.07f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NexoraViolet.copy(alpha = 0.16f)),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = NexoraViolet)
                Column {
                    Text("Protección Nexora", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Tokens rotables, Keystore y sesiones revocables por dispositivo.",
                        color = NexoraMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            enabled = !actionLoading,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3A171A),
                contentColor = Color(0xFFFFD6D9),
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Cerrar sesión en este dispositivo", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun ProfileCard(
    user: NexoraUser,
    emailVerified: Boolean,
    editingName: Boolean,
    name: String,
    actionLoading: Boolean,
    onNameChange: (String) -> Unit,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveName: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NexoraSurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    NexoraAccent.copy(alpha = 0.35f),
                                    NexoraViolet.copy(alpha = 0.28f),
                                ),
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        user.name.firstOrNull()?.uppercase() ?: "N",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user.name,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            user.email ?: "Cuenta social",
                            color = NexoraMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (emailVerified) {
                            Spacer(Modifier.size(5.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Correo verificado",
                                tint = NexoraAccent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                if (!editingName) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar perfil")
                    }
                }
            }

            if (editingName) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancelEdit, enabled = !actionLoading) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = onSaveName,
                        enabled = !actionLoading && name.trim().length >= 2,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailVerificationCard(
    email: String,
    code: String,
    requested: Boolean,
    loading: Boolean,
    onCodeChange: (String) -> Unit,
    onRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NexoraBlue.copy(alpha = 0.07f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, NexoraBlue.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, tint = NexoraBlue)
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Verifica tu correo", fontWeight = FontWeight.Bold)
                    Text(email, color = NexoraMuted, fontSize = 11.sp)
                }
            }
            Text(
                "La verificación protege recuperación de contraseña y cambios sensibles.",
                color = Color(0xFFD7E7FF),
                fontSize = 12.sp,
            )
            if (requested) {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Código de 6 dígitos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                )
                Button(
                    onClick = onConfirm,
                    enabled = !loading && code.length == 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Confirmar correo")
                }
            }
            OutlinedButton(
                onClick = onRequest,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (requested) "Enviar código de nuevo" else "Enviar código")
            }
        }
    }
}

@Composable
private fun ProvidersCard(
    providers: List<String>,
    hasPassword: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NexoraSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = NexoraAccent)
                Spacer(Modifier.size(9.dp))
                Column {
                    Text("Métodos de acceso", fontWeight = FontWeight.Bold)
                    Text("Identidades conectadas a esta cuenta", color = NexoraMuted, fontSize = 11.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (hasPassword) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Correo") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = NexoraAccent.copy(alpha = 0.08f),
                        ),
                    )
                }
                providers.forEach { provider ->
                    AssistChip(
                        onClick = {},
                        label = { Text(provider.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            if (providers.isEmpty() && !hasPassword) {
                Text("No hay métodos disponibles.", color = NexoraMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SessionsCard(
    sessions: List<NexoraAccountSession>,
    loading: Boolean,
    onRevoke: (String) -> Unit,
    onRevokeOthers: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NexoraSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = NexoraViolet)
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dispositivos y sesiones", fontWeight = FontWeight.Bold)
                    Text(
                        "${sessions.size} sesión(es) activa(s)",
                        color = NexoraMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(color = NexoraDivider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (session.current) {
                                    NexoraAccent.copy(alpha = 0.12f)
                                } else {
                                    Color.White.copy(alpha = 0.04f)
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Devices,
                            contentDescription = null,
                            tint = if (session.current) NexoraAccent else NexoraMuted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            session.deviceName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (session.current) {
                                "Este dispositivo · activo ahora"
                            } else {
                                "Último uso ${compactDate(session.lastUsedAt)}"
                            },
                            color = NexoraMuted,
                            fontSize = 11.sp,
                        )
                    }
                    if (!session.current) {
                        TextButton(
                            onClick = { onRevoke(session.id) },
                            enabled = !loading,
                        ) {
                            Text("Cerrar")
                        }
                    }
                }
            }
            if (sessions.count { !it.current } > 0) {
                FilledTonalButton(
                    onClick = onRevokeOthers,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("Cerrar todas las demás sesiones")
                }
            }
        }
    }
}

private fun compactDate(value: String): String {
    if (value.length < 10) return "recientemente"
    val date = value.take(10)
    return date.split('-').let { parts ->
        if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else date
    }
}

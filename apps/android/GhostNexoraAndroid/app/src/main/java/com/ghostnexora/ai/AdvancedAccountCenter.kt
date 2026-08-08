package com.ghostnexora.ai

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AdvancedAccountCenter(
    authStore: AuthStore,
    initialUser: NexoraUser,
    externalMessage: String?,
    onUserUpdated: (NexoraUser) -> Unit,
    onExternalMessageConsumed: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var overview by remember { mutableStateOf<NexoraAccountOverview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var actionLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(externalMessage) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(initialUser.name) }
    var verificationRequested by rememberSaveable { mutableStateOf(false) }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var passwordEditor by rememberSaveable { mutableStateOf(false) }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    fun applyOverview(next: NexoraAccountOverview) {
        overview = next
        name = next.user.name
        onUserUpdated(next.user)
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching { AuthApi.getAccountOverview(authStore) }
            }
            result.onSuccess(::applyOverview).onFailure {
                error = it.message ?: "No se pudo cargar la cuenta."
            }
            loading = false
        }
    }

    fun runAction(
        successMessage: String,
        refreshAfter: Boolean = true,
        operation: () -> Unit,
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
                    if (next != null) applyOverview(next)
                }
            }.onFailure {
                error = it.message ?: "No se pudo completar la operación."
            }
            actionLoading = false
        }
    }

    fun linkProvider(provider: String) {
        scope.launch {
            actionLoading = true
            error = null
            notice = null
            val result = withContext(Dispatchers.IO) {
                runCatching { AuthApi.socialLinkStart(provider, authStore) }
            }
            result.onSuccess { uri ->
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }.onFailure {
                error = it.message ?: "No se pudo abrir el proveedor."
            }
            actionLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(externalMessage) {
        if (!externalMessage.isNullOrBlank()) {
            notice = externalMessage
            onExternalMessageConsumed()
        }
    }

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
                Text("Perfil, accesos y seguridad", color = NexoraMuted, fontSize = 12.sp)
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
            AccountProfileCard(
                user = account.user,
                emailVerified = account.emailVerified,
                editing = editingName,
                name = name,
                loading = actionLoading,
                onNameChange = { name = it.take(80) },
                onEdit = { editingName = true },
                onCancel = {
                    editingName = false
                    name = account.user.name
                },
                onSave = {
                    runAction("Nombre actualizado.") {
                        val updated = AuthApi.updateAccountName(authStore, name)
                        authStore.loadSession()?.let { current ->
                            authStore.saveSession(current.copy(user = updated))
                        }
                    }
                    editingName = false
                },
            )

            if (account.user.email != null && !account.emailVerified) {
                AccountEmailVerificationCard(
                    email = account.user.email,
                    code = verificationCode,
                    requested = verificationRequested,
                    loading = actionLoading,
                    onCodeChange = { verificationCode = it.filter(Char::isDigit).take(6) },
                    onRequest = {
                        runAction(
                            successMessage = "Código enviado por Nexora Mail.",
                            refreshAfter = false,
                        ) { AuthApi.requestEmailVerification(authStore) }
                        verificationRequested = true
                    },
                    onConfirm = {
                        runAction("Correo verificado correctamente.") {
                            AuthApi.confirmEmailVerification(authStore, verificationCode)
                        }
                        verificationCode = ""
                    },
                )
            }

            AccountProvidersCard(
                providers = account.providers,
                hasPassword = account.hasPassword,
                loading = actionLoading,
                onLink = ::linkProvider,
                onUnlink = { provider ->
                    runAction("Proveedor desvinculado.") {
                        AuthApi.unlinkProvider(provider, authStore)
                    }
                },
            )

            AccountPasswordCard(
                hasPassword = account.hasPassword,
                emailVerified = account.emailVerified,
                expanded = passwordEditor,
                currentPassword = currentPassword,
                newPassword = newPassword,
                confirmPassword = confirmPassword,
                loading = actionLoading,
                onExpand = { passwordEditor = true },
                onCancel = {
                    passwordEditor = false
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                },
                onCurrentChange = { currentPassword = it.take(128) },
                onNewChange = { newPassword = it.take(128) },
                onConfirmChange = { confirmPassword = it.take(128) },
                onSave = {
                    if (newPassword != confirmPassword) {
                        error = "Las contraseñas nuevas no coinciden."
                    } else {
                        runAction(
                            successMessage = if (account.hasPassword) {
                                "Contraseña actualizada. Las demás sesiones se cerraron."
                            } else {
                                "Contraseña añadida a tu cuenta."
                            },
                        ) {
                            AuthApi.changePassword(authStore, currentPassword, newPassword)
                        }
                        passwordEditor = false
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                    }
                },
            )

            AccountSessionsCard(
                sessions = account.sessions,
                loading = actionLoading,
                onRevoke = { sessionId ->
                    runAction("Sesión cerrada.") { AuthApi.revokeSession(authStore, sessionId) }
                },
                onRevokeOthers = {
                    runAction("Se cerraron las demás sesiones.") {
                        AuthApi.revokeOtherSessions(authStore)
                    }
                },
            )
        }

        notice?.let { AccountNotice(it, error = false) }
        error?.let { AccountNotice(it, error = true) }

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
                        "PKCE, Keystore, sesiones revocables y vínculos sociales explícitos.",
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
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountProfileCard(
    user: NexoraUser,
    emailVerified: Boolean,
    editing: Boolean,
    name: String,
    loading: Boolean,
    onNameChange: (String) -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NexoraSurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexoraUserAvatar(user = user, size = 54.dp)
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
                if (!editing) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar perfil")
                    }
                }
            }
            if (editing) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel, enabled = !loading) { Text("Cancelar") }
                    Button(
                        onClick = onSave,
                        enabled = !loading && name.trim().length >= 2,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Guardar") }
                }
            }
        }
    }
}

@Composable
private fun AccountEmailVerificationCard(
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
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, tint = NexoraBlue)
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Verifica tu correo", fontWeight = FontWeight.Bold)
                    Text(email, color = NexoraMuted, fontSize = 11.sp)
                }
            }
            Text(
                "Nexora Mail enviará el código desde tu propia VPS.",
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
                ) { Text("Confirmar correo") }
            }
            OutlinedButton(
                onClick = onRequest,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (requested) "Enviar código de nuevo" else "Enviar código") }
        }
    }
}

@Composable
private fun AccountProvidersCard(
    providers: List<String>,
    hasPassword: Boolean,
    loading: Boolean,
    onLink: (String) -> Unit,
    onUnlink: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NexoraSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = NexoraAccent)
                Spacer(Modifier.size(9.dp))
                Column {
                    Text("Métodos de acceso", fontWeight = FontWeight.Bold)
                    Text("Vinculación explícita y reversible", color = NexoraMuted, fontSize = 11.sp)
                }
            }
            if (hasPassword) {
                Text("Correo y contraseña · activo", color = Color(0xFFD8FFF4), fontSize = 12.sp)
            }
            listOf("google", "facebook", "discord").forEach { provider ->
                val linked = provider in providers
                val label = provider.replaceFirstChar { it.uppercase() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        when (provider) {
                            "discord" -> Icons.Default.SportsEsports
                            else -> Icons.Default.Language
                        },
                        contentDescription = null,
                        tint = if (linked) NexoraAccent else NexoraMuted,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (linked) "Vinculado" else "No vinculado",
                            color = NexoraMuted,
                            fontSize = 11.sp,
                        )
                    }
                    if (linked) {
                        TextButton(onClick = { onUnlink(provider) }, enabled = !loading) {
                            Text("Quitar")
                        }
                    } else {
                        FilledTonalButton(onClick = { onLink(provider) }, enabled = !loading) {
                            Text("Vincular")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPasswordCard(
    hasPassword: Boolean,
    emailVerified: Boolean,
    expanded: Boolean,
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    loading: Boolean,
    onExpand: () -> Unit,
    onCancel: () -> Unit,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NexoraSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = NexoraViolet)
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (hasPassword) "Cambiar contraseña" else "Añadir contraseña", fontWeight = FontWeight.Bold)
                    Text(
                        if (hasPassword) "Mantén el acceso por correo protegido" else "Añade acceso por correo a tu cuenta social",
                        color = NexoraMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            if (!hasPassword && !emailVerified) {
                Text(
                    "Primero verifica el correo de la cuenta.",
                    color = Color(0xFFFBCF8A),
                    fontSize = 12.sp,
                )
            } else if (!expanded) {
                OutlinedButton(
                    onClick = onExpand,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (hasPassword) "Cambiar contraseña" else "Crear contraseña") }
            } else {
                if (hasPassword) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = onCurrentChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Contraseña actual") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = onNewChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nueva contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = onConfirmChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Confirmar contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel, enabled = !loading) { Text("Cancelar") }
                    Button(
                        onClick = onSave,
                        enabled = !loading && newPassword.length >= 8 && confirmPassword.length >= 8 &&
                            (!hasPassword || currentPassword.isNotBlank()),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Guardar") }
                }
            }
        }
    }
}

@Composable
private fun AccountSessionsCard(
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
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = NexoraViolet)
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dispositivos y sesiones", fontWeight = FontWeight.Bold)
                    Text("${sessions.size} sesión(es) activa(s)", color = NexoraMuted, fontSize = 11.sp)
                }
            }
            sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(color = NexoraDivider)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = null,
                        tint = if (session.current) NexoraAccent else NexoraMuted,
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.deviceName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            if (session.current) "Este dispositivo · activo ahora" else "Último uso ${compactAccountDate(session.lastUsedAt)}",
                            color = NexoraMuted,
                            fontSize = 11.sp,
                        )
                    }
                    if (!session.current) {
                        TextButton(onClick = { onRevoke(session.id) }, enabled = !loading) { Text("Cerrar") }
                    }
                }
            }
            if (sessions.any { !it.current }) {
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

@Composable
private fun AccountNotice(message: String, error: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (error) Color(0xFFEF4444).copy(alpha = 0.10f) else NexoraAccent.copy(alpha = 0.09f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (error) Color(0xFFEF4444).copy(alpha = 0.25f) else NexoraAccent.copy(alpha = 0.20f),
        ),
    ) {
        Text(
            message,
            modifier = Modifier.padding(13.dp),
            color = if (error) Color(0xFFFECACA) else Color(0xFFD5FFF3),
            fontSize = 12.sp,
        )
    }
}

private fun compactAccountDate(value: String): String {
    if (value.length < 10) return "recientemente"
    val parts = value.take(10).split('-')
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else value.take(10)
}

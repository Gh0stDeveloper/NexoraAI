package com.ghostnexora.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AuthScreen(
    loading: Boolean,
    error: String?,
    onSocial: (String) -> Unit,
    onEmail: (Boolean, String, String, String) -> Unit,
    onClearError: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(AuthScreenMode.WELCOME) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF080A0F),
                        Color(0xFF0D111A),
                        Color(0xFF090A0D),
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            NexoraAccent.copy(alpha = 0.20f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(260.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            NexoraViolet.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (mode != AuthScreenMode.WELCOME) {
                IconButton(
                    onClick = {
                        onClearError()
                        mode = AuthScreenMode.WELCOME
                    },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            } else {
                Spacer(Modifier.height(38.dp))
            }

            NexoraMark(82.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (mode == AuthScreenMode.WELCOME) "Tu espacio de inteligencia" else {
                    if (mode == AuthScreenMode.REGISTER) "Crea tu cuenta" else "Bienvenido de nuevo"
                },
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 29.sp,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.7).sp,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = if (mode == AuthScreenMode.WELCOME) {
                    "Inicia sesión para mantener tus chats, proyectos y respuestas sincronizados en Nexora AI."
                } else {
                    "Tu historial se guarda de forma segura en tu propia infraestructura de Nexora AI."
                },
                color = NexoraMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))

            FeatureStrip()
            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NexoraSurface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    if (mode == AuthScreenMode.WELCOME) {
                        SocialButton(
                            title = "Continuar con Google",
                            icon = Icons.Default.Language,
                            iconTint = Color(0xFF8AB4F8),
                            enabled = !loading,
                            onClick = { onSocial("google") },
                        )
                        SocialButton(
                            title = "Continuar con Facebook",
                            icon = Icons.Default.Facebook,
                            iconTint = Color(0xFF74A9FF),
                            enabled = !loading,
                            onClick = { onSocial("facebook") },
                        )
                        SocialButton(
                            title = "Continuar con Discord",
                            icon = Icons.Default.SportsEsports,
                            iconTint = Color(0xFF9AA7FF),
                            enabled = !loading,
                            onClick = { onSocial("discord") },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.08f),
                            )
                            Text("o", color = NexoraMuted, fontSize = 12.sp)
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.08f),
                            )
                        }

                        Button(
                            onClick = {
                                onClearError()
                                mode = AuthScreenMode.LOGIN
                            },
                            enabled = !loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexoraAccent,
                                contentColor = Color(0xFF04130E),
                            ),
                        ) {
                            Icon(Icons.Default.AlternateEmail, contentDescription = null)
                            Spacer(Modifier.size(9.dp))
                            Text("Continuar con correo", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val register = mode == AuthScreenMode.REGISTER
                        if (register) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(80) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Nombre") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                shape = RoundedCornerShape(18.dp),
                            )
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.take(160) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Correo electrónico") },
                            leadingIcon = {
                                Icon(Icons.Default.AlternateEmail, contentDescription = null)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(18.dp),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it.take(128) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Contraseña") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) {
                                            "Ocultar contraseña"
                                        } else {
                                            "Mostrar contraseña"
                                        },
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(18.dp),
                        )

                        Button(
                            onClick = { onEmail(register, name, email, password) },
                            enabled = !loading && email.isNotBlank() && password.length >= 8 &&
                                (!register || name.trim().length >= 2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    if (register) "Crear cuenta" else "Iniciar sesión",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                onClearError()
                                mode = if (register) AuthScreenMode.LOGIN else AuthScreenMode.REGISTER
                            },
                            enabled = !loading,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                if (register) {
                                    "Ya tengo cuenta"
                                } else {
                                    "Crear una cuenta con correo"
                                },
                            )
                        }
                    }

                    if (loading && mode == AuthScreenMode.WELCOME) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(9.dp))
                            Text("Abriendo proveedor seguro…", color = NexoraMuted, fontSize = 12.sp)
                        }
                    }

                    if (!error.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                error,
                                modifier = Modifier.padding(13.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Al continuar aceptas los Términos y la Política de privacidad de Nexora AI.",
                color = NexoraMuted.copy(alpha = 0.85f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun FeatureStrip() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = NexoraAccent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text("Chats sincronizados", color = Color(0xFFD4EDE5), fontSize = 12.sp)
        Spacer(Modifier.size(13.dp))
        Box(
            modifier = Modifier
                .size(3.dp)
                .background(NexoraMuted, CircleShape),
        )
        Spacer(Modifier.size(13.dp))
        Text("Sesión protegida", color = Color(0xFFD4EDE5), fontSize = 12.sp)
    }
}

@Composable
private fun SocialButton(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.11f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.025f),
            contentColor = Color.White,
        ),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

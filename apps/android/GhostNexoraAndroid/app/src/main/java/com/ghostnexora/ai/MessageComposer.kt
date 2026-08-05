package com.ghostnexora.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class ComposerPanel {
    ACTIONS,
    MODELS,
    INTELLIGENCE,
}

@Composable
internal fun MessageComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    attachments: List<PendingAttachment>,
    loading: Boolean,
    panel: ComposerPanel?,
    onPanelChange: (ComposerPanel?) -> Unit,
    selectedModel: NexoraModel,
    intelligence: IntelligenceLevel,
    validateCode: Boolean,
    onImage: () -> Unit,
    onFile: () -> Unit,
    onModelSelected: (NexoraModel) -> Unit,
    onIntelligenceSelected: (IntelligenceLevel) -> Unit,
    onValidateCodeChange: (Boolean) -> Unit,
    onRemoveAttachment: (PendingAttachment) -> Unit,
    onSend: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    fun keepInputFocused() {
        if (!imeVisible) return
        scope.launch {
            delay(40)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        color = Color(0xFF080C13),
        tonalElevation = 10.dp,
        shadowElevation = 14.dp,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = panel != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ComposerOptionsPanel(
                    panel = panel ?: ComposerPanel.ACTIONS,
                    selectedModel = selectedModel,
                    intelligence = intelligence,
                    validateCode = validateCode,
                    onNavigate = {
                        onPanelChange(it)
                        keepInputFocused()
                    },
                    onClose = {
                        onPanelChange(null)
                        keepInputFocused()
                    },
                    onImage = onImage,
                    onFile = onFile,
                    onModelSelected = {
                        onModelSelected(it)
                        onPanelChange(ComposerPanel.ACTIONS)
                        keepInputFocused()
                    },
                    onIntelligenceSelected = {
                        onIntelligenceSelected(it)
                        onPanelChange(ComposerPanel.ACTIONS)
                        keepInputFocused()
                    },
                    onValidateCodeChange = {
                        onValidateCodeChange(it)
                        keepInputFocused()
                    },
                )
            }

            AnimatedVisibility(attachments.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.height((attachments.size.coerceAtMost(3) * 42).dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(attachments, key = { it.id }) { attachment ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    attachment.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (attachment.mimeType.startsWith("image/")) {
                                        Icons.Default.Image
                                    } else {
                                        Icons.Default.AttachFile
                                    },
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar adjunto",
                                    modifier = Modifier.clickable {
                                        onRemoveAttachment(attachment)
                                    },
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFF121C28),
                            ),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledIconButton(
                    onClick = {
                        onPanelChange(
                            if (panel == null) ComposerPanel.ACTIONS else null,
                        )
                        keepInputFocused()
                    },
                    enabled = !loading,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (panel == null) Icons.Default.Add else Icons.Default.Close,
                        contentDescription = "Abrir opciones",
                    )
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { Text("Mensaje para Nexora AI") },
                    keyboardActions = KeyboardActions(),
                )

                FilledIconButton(
                    onClick = onSend,
                    enabled = !loading && (prompt.isNotBlank() || attachments.isNotEmpty()),
                    modifier = Modifier.size(48.dp),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerOptionsPanel(
    panel: ComposerPanel,
    selectedModel: NexoraModel,
    intelligence: IntelligenceLevel,
    validateCode: Boolean,
    onNavigate: (ComposerPanel) -> Unit,
    onClose: () -> Unit,
    onImage: () -> Unit,
    onFile: () -> Unit,
    onModelSelected: (NexoraModel) -> Unit,
    onIntelligenceSelected: (IntelligenceLevel) -> Unit,
    onValidateCodeChange: (Boolean) -> Unit,
) {
    Surface(
        color = NexoraSurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (panel) {
                            ComposerPanel.ACTIONS -> "Herramientas"
                            ComposerPanel.MODELS -> "Seleccionar modelo"
                            ComposerPanel.INTELLIGENCE -> "Nivel de inteligencia"
                        },
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        when (panel) {
                            ComposerPanel.ACTIONS ->
                                "${selectedModel.label} · ${intelligence.agentCount} agente(s)"
                            ComposerPanel.MODELS ->
                                "Especialidad que atenderá la solicitud"
                            ComposerPanel.INTELLIGENCE ->
                                "Más agentes implican más revisión y tiempo"
                        },
                        color = NexoraMuted,
                        fontSize = 12.sp,
                    )
                }
                FilledIconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar opciones")
                }
            }

            when (panel) {
                ComposerPanel.ACTIONS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Image,
                            title = "Imagen",
                            subtitle = "Analizar una foto",
                            onClick = onImage,
                        )
                        OptionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.AttachFile,
                            title = "Archivo",
                            subtitle = "PDF, Word o código",
                            onClick = onFile,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.ModelTraining,
                            title = "Modelo",
                            subtitle = selectedModel.label,
                            onClick = { onNavigate(ComposerPanel.MODELS) },
                        )
                        OptionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Speed,
                            title = "Inteligencia",
                            subtitle = intelligence.label,
                            onClick = { onNavigate(ComposerPanel.INTELLIGENCE) },
                        )
                    }
                    OptionCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Science,
                        title = if (validateCode) "Pruebas activadas" else "Probar código",
                        subtitle = if (validateCode) {
                            "Laboratorio efímero · toca para desactivar"
                        } else {
                            "Valida Python, JavaScript o Bash de forma aislada"
                        },
                        onClick = { onValidateCodeChange(!validateCode) },
                    )
                }

                ComposerPanel.MODELS -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(NexoraModel.entries) { model ->
                            SelectableOptionCard(
                                title = model.label,
                                subtitle = model.description,
                                badge = modelBadge(model),
                                selected = model == selectedModel,
                                onClick = { onModelSelected(model) },
                            )
                        }
                    }
                }

                ComposerPanel.INTELLIGENCE -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(IntelligenceLevel.entries) { level ->
                            SelectableOptionCard(
                                title = level.label,
                                subtitle = level.description,
                                badge = "${level.agentCount}",
                                selected = level == intelligence,
                                onClick = { onIntelligenceSelected(level) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color(0xFF0C141E),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(NexoraAccent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = NexoraAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = NexoraMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SelectableOptionCard(
    title: String,
    subtitle: String,
    badge: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(width = 190.dp, height = 104.dp)
            .clickable(onClick = onClick),
        color = if (selected) {
            NexoraAccent.copy(alpha = 0.13f)
        } else {
            Color(0xFF0C141E)
        },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (selected) {
                NexoraAccent.copy(alpha = 0.72f)
            } else {
                Color.White.copy(alpha = 0.07f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = NexoraAccent.copy(alpha = 0.16f),
                    shape = CircleShape,
                ) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = NexoraAccent,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                    )
                }
            }
            Text(
                subtitle,
                color = NexoraMuted,
                fontSize = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun modelBadge(model: NexoraModel): String = when (model) {
    NexoraModel.AUTO -> "AUTO"
    NexoraModel.FULL_STACK -> "WEB"
    NexoraModel.ANDROID -> "APP"
    NexoraModel.BACKEND -> "API"
    NexoraModel.SECURITY -> "SEC"
    NexoraModel.DATA -> "SQL"
    NexoraModel.DEVOPS -> "OPS"
}

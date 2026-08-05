package com.ghostnexora.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal enum class LegalDocument(val title: String) {
    TERMS("Términos y condiciones"),
    PRIVACY("Aviso de privacidad"),
}

private data class LegalSection(val title: String, val body: String)

private val terms = listOf(
    LegalSection(
        "1. Aceptación",
        "Al usar Nexora AI aceptas estos términos. Si no estás de acuerdo, no utilices la aplicación ni la API. La fecha de vigencia es el 5 de agosto de 2026.",
    ),
    LegalSection(
        "2. Servicio",
        "Nexora AI ofrece asistencia automatizada para programación, datos, operaciones y ciberseguridad defensiva. Las respuestas pueden contener errores y deben revisarse antes de usarse en producción.",
    ),
    LegalSection(
        "3. Uso permitido",
        "Debes usar el servicio solo sobre sistemas propios o con autorización. Se prohíben malware, phishing, robo de credenciales, evasión, exfiltración, abuso de terceros y cualquier actividad ilegal.",
    ),
    LegalSection(
        "4. Código y laboratorio",
        "El código generado no incluye garantía. Cuando el laboratorio está habilitado, las pruebas se ejecutan en contenedores efímeros con límites; un resultado correcto no sustituye auditorías, pruebas completas ni revisión humana.",
    ),
    LegalSection(
        "5. Disponibilidad",
        "El servicio depende de la VPS, la conectividad y los modelos instalados. Puede pausarse por mantenimiento, seguridad o falta de recursos sin garantía de disponibilidad continua.",
    ),
    LegalSection(
        "6. Responsabilidad",
        "Eres responsable de validar respuestas, copias de seguridad, permisos y despliegues. Nexora AI se ofrece sin garantías expresas y no responde por daños derivados de decisiones tomadas sin verificación adecuada.",
    ),
    LegalSection(
        "7. Contacto",
        "Para consultas sobre estos términos escribe a ghostnexora@gmail.com.",
    ),
)

private val privacy = listOf(
    LegalSection(
        "1. Datos procesados",
        "La aplicación envía a tu servidor los mensajes, archivos que adjuntes, identificadores locales de chat/proyecto, modelo elegido y datos técnicos mínimos de versión necesarios para responder.",
    ),
    LegalSection(
        "2. Historial local",
        "Los chats, proyectos, fijados y metadatos de actividad se guardan localmente en el dispositivo. Desinstalar la aplicación o borrar sus datos puede eliminar ese historial.",
    ),
    LegalSection(
        "3. Finalidad",
        "La información se usa para generar respuestas, analizar adjuntos, ejecutar funciones solicitadas, proteger el servicio, diagnosticar fallos y mejorar su operación.",
    ),
    LegalSection(
        "4. Modelos y VPS",
        "Por defecto, Nexora AI procesa inferencias con modelos alojados en la VPS configurada. El operador debe revisar sus propios logs, copias de seguridad y políticas de retención.",
    ),
    LegalSection(
        "5. Seguridad",
        "Se usa HTTPS en producción y se limita el tráfico claro a destinos locales de desarrollo. Ningún sistema es infalible; evita adjuntar secretos, claves privadas o datos que no deban procesarse.",
    ),
    LegalSection(
        "6. Derechos y contacto",
        "Puedes borrar el historial desde la aplicación o sus datos desde Android. Para solicitudes relacionadas con privacidad escribe a ghostnexora@gmail.com.",
    ),
)

@Composable
internal fun LegalDocumentDialog(document: LegalDocument, onDismiss: () -> Unit) {
    val sections = if (document == LegalDocument.TERMS) terms else privacy
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            color = Color(0xFF0C131D),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(document.title, fontWeight = FontWeight.Black, fontSize = 21.sp)
                        Text("Nexora AI · versión ${BuildConfig.VERSION_NAME}", color = NexoraMuted, fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(sections) { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(section.title, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(section.body, color = Color(0xFFC5D0D8), lineHeight = 21.sp)
                        }
                    }
                }
            }
        }
    }
}

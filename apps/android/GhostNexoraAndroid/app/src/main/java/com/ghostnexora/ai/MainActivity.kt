package com.ghostnexora.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexoraApp() }
    }
}

data class ChatMessage(val role: String, val content: String)

private val modes = listOf("auto", "fullstack", "android", "backend", "security", "data", "devops")

@Composable
fun NexoraApp() {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "assistant",
                "Nexora AI cliente Android listo. Conectado por defecto a ${BuildConfig.DEFAULT_API_BASE_URL}."
            )
        )
    }
    var prompt by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("auto") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF10A37F),
            background = Color(0xFF070A12),
            surface = Color(0xFF0D1320),
            onSurface = Color(0xFFF8FAFC)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF07120F), Color(0xFF070A12), Color(0xFF0B1020))
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Header()
                ModeSelector(selected = mode, onSelected = { mode = it })

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message -> MessageBubble(message) }
                    if (loading) {
                        item { MessageBubble(ChatMessage("assistant", "Pensando con el agente $mode...")) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 5,
                        shape = RoundedCornerShape(22.dp),
                        placeholder = { Text("Mensaje para Nexora AI") }
                    )
                    Button(
                        enabled = prompt.isNotBlank() && !loading,
                        onClick = {
                            val message = prompt.trim()
                            prompt = ""
                            messages.add(ChatMessage("user", message))
                            loading = true
                            scope.launch {
                                val answer = withContext(Dispatchers.IO) { postChat(message, mode) }
                                messages.add(ChatMessage("assistant", answer))
                                loading = false
                            }
                        }
                    ) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Enviar")
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC111827)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF10A37F), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("NX", color = Color(0xFF03140F), fontWeight = FontWeight.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Nexora AI", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Cliente Android · ${BuildConfig.VERSION_NAME}", color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun ModeSelector(selected: String, onSelected: (String) -> Unit) {
    LazyColumn(modifier = Modifier.height(112.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(modes.chunked(3)) { rowModes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowModes.forEach { item ->
                    FilterChip(
                        selected = selected == item,
                        onClick = { onSelected(item) },
                        label = { Text(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.94f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF0F6F5C) else Color(0xFF111827)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(if (isUser) "Tú" else "Nexora AI", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(message.content, color = Color(0xFFE5E7EB), lineHeight = 21.sp)
            }
        }
    }
}

private fun postChat(message: String, mode: String): String {
    return try {
        val url = URL(BuildConfig.DEFAULT_API_BASE_URL + "api/mobile/chat")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Nexora-Client", "android")
            setRequestProperty("X-Nexora-Version", BuildConfig.VERSION_NAME)
            doOutput = true
        }

        val payload = JSONObject()
            .put("message", message)
            .put("mode", mode)
            .put("client", "android")
            .toString()

        connection.outputStream.use { stream -> stream.write(payload.toByteArray()) }
        val body = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().readText()
        } else {
            connection.errorStream?.bufferedReader()?.readText().orEmpty()
        }

        val parsed = JSONObject(body)
        parsed.optString("answer", parsed.optString("error", body))
    } catch (error: Exception) {
        "Error conectando con la API: ${error.message}"
    }
}

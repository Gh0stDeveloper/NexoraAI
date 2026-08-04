package com.ghostnexora.ai
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{NexoraApp()}}}
@Composable fun NexoraApp(){var prompt by remember{mutableStateOf("")};var answer by remember{mutableStateOf("Conectado a ${BuildConfig.DEFAULT_API_BASE_URL}")};val scope=rememberCoroutineScope();MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF10A37F),background=Color(0xFF0B0F19))){Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text("Nexora AI",style=MaterialTheme.typography.headlineLarge);Card(shape=RoundedCornerShape(24.dp)){Text(answer,Modifier.padding(18.dp))};OutlinedTextField(prompt,{prompt=it},Modifier.fillMaxWidth(),placeholder={Text("Mensaje")});Button(onClick={scope.launch(Dispatchers.IO){answer=postChat(prompt)}}){Text("Enviar")}}}}
fun postChat(message:String):String{return try{val url=URL(BuildConfig.DEFAULT_API_BASE_URL+"api/mobile/chat");val c=url.openConnection() as HttpURLConnection;c.requestMethod="POST";c.setRequestProperty("Content-Type","application/json");c.doOutput=true;c.outputStream.use{it.write("{\"message\":\"${message.replace("\"","\\\"")}\",\"mode\":\"auto\"}".toByteArray())};c.inputStream.bufferedReader().readText()}catch(e:Exception){"Error conectando API: ${e.message}"}}

package com.ghostnexora.generated

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val content = assets.open("nexora_content.txt").bufferedReader().use { it.readText() }
        setContent { GeneratedApplication(content) }
    }
}

@Composable
private fun GeneratedApplication(content: String) {
    val accent = colorResource(R.color.generated_accent)
    val sections = remember(content) {
        content.split(Regex("\\n\\s*\\n"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(30)
    }
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = accent,
            background = Color(0xFF070B0D),
            surface = Color(0xFF11171A),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF071510), Color(0xFF070B0D), Color(0xFF0B1016)),
                    ),
                ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Creada con Nexora AI",
                            color = accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                items(sections) { section ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xD911171A),
                        shape = RoundedCornerShape(22.dp),
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = section.removePrefix("#").trim(),
                            modifier = Modifier.padding(18.dp),
                            color = Color(0xFFE8EEF0),
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                        )
                    }
                }
            }
        }
    }
}

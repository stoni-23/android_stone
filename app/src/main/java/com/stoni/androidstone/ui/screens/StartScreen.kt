package com.stoni.androidstone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bubbles = listOf(
    "Reichszeitglocke V-3 — Ursprung. Zu laut für den Orbit.",
    "Dann dämpfen wir den Lärm."
)

@Composable
fun StartScreen(onStart: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))
            .clickable {
                if (step < bubbles.lastIndex) step++ else onStart()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text("STARGAME", color = Color(0xFFC9A66B), fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))
            Text(
                text = bubbles[step],
                color = Color(0xFFF2E6D0),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color(0xFF1A1A28))
                    .padding(16.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (step < bubbles.lastIndex) "Tippen …" else "Tippen zum Start",
                color = Color(0xFF8A8680),
                fontSize = 12.sp
            )
        }
    }
}

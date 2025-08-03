package com.wordslop.game.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordslop.game.model.GamePlayer
import com.wordslop.game.model.GameMode

@Composable
fun WordGameScreen(
    gamePlayers: List<GamePlayer>,
    gameMode: GameMode,
    onBackToLobby: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Word Game",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Players: ${gamePlayers.size}")
        Text("Mode: ${gameMode.name}")
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "🚧 Game UI Coming Soon! 🚧",
            fontSize = 18.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        onBackToLobby?.let { callback ->
            Button(onClick = callback) {
                Text("Back to Lobby")
            }
        }
    }
}
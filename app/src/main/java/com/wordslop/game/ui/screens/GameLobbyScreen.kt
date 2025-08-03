package com.wordslop.game.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordslop.game.auth.UserInfo
import com.wordslop.game.model.GameLobby

@Composable
fun GameLobbyScreen(
    gameLobby: GameLobby,
    currentUser: UserInfo,
    onBack: () -> Unit,
    onReady: () -> Unit,
    onStartGame: () -> Unit,
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
            text = "Game Lobby",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Game ID: ${gameLobby.gameId}")
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onReady) {
            Text("Ready")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (gameLobby.hostUserId == currentUser.userId) {
            Button(onClick = onStartGame) {
                Text("Start Game")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Button(onClick = onBack) {
            Text("Leave Lobby")
        }
    }
}
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
fun JoinGameScreen(
    userInfo: UserInfo,
    availableLobbies: List<GameLobby>,
    onBack: () -> Unit,
    onJoinLobby: (GameLobby) -> Unit,
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
            text = "Join Game",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (availableLobbies.isEmpty()) {
            Text("No games available")
        } else {
            Text("Available Games: ${availableLobbies.size}")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
package com.wordslop.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordslop.game.auth.UserInfo
import com.wordslop.game.model.GameLobby
import com.wordslop.game.model.LobbyPlayer
import com.wordslop.game.model.GameStatus
import com.wordslop.game.model.GameMode

@Composable
fun JoinGameScreen(
    userInfo: UserInfo,
    availableLobbies: List<GameLobby>,
    onBack: () -> Unit,
    onJoinLobby: (GameLobby) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayLobbies = availableLobbies

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left column (30%) - Available Games header
        Column(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Available Games",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Main")
            }
        }
        
        // Right column (70%) - Games list
        Column(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(displayLobbies) { lobby ->
                    GameLobbyCard(
                        lobby = lobby,
                        onJoin = { onJoinLobby(lobby) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameLobbyCard(
    lobby: GameLobby,
    onJoin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Gray.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Game info
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${lobby.numberOfRounds}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Rounds",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${lobby.players.size}/${lobby.maxPlayers}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Players",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
            
            // Right side - Join button
            Button(
                onClick = onJoin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Join")
            }
        }
    }
}
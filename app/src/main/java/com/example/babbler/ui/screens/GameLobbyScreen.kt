package com.example.wordslop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
// Removed problematic icon import
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wordslop.model.GameLobby
import com.example.wordslop.model.LobbyPlayer
import com.example.wordslop.auth.UserInfo

@Composable
fun GameLobbyScreen(
    gameLobby: GameLobby,
    currentUser: UserInfo,
    onBack: () -> Unit,
    onReady: () -> Unit,
    onStartGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPlayer = gameLobby.players.find { it.userId == currentUser.userId }
    val isHost = currentPlayer?.isHost == true
    val allPlayersReady = gameLobby.players.isNotEmpty() && gameLobby.players.all { it.isReady }
    val canStartGame = isHost && (allPlayersReady || gameLobby.players.size == 1) // Allow single player for testing
    val isFullLobby = gameLobby.players.size >= gameLobby.maxPlayers
    
    // Auto-start countdown when lobby is full
    var autoStartCountdown by remember { mutableStateOf<Int?>(null) }
    
    LaunchedEffect(isFullLobby) {
        if (isFullLobby && autoStartCountdown == null) {
            autoStartCountdown = 10 // 10 second countdown
        } else if (!isFullLobby) {
            autoStartCountdown = null
        }
    }
    
    LaunchedEffect(autoStartCountdown) {
        if (autoStartCountdown != null && autoStartCountdown!! > 0) {
            kotlinx.coroutines.delay(1000)
            autoStartCountdown = autoStartCountdown!! - 1
        } else if (autoStartCountdown == 0) {
            onStartGame()
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = "Game Lobby",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = if (gameLobby.isPublic) "Public Game" else "Private Game",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Game Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1F2937)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Rounds",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${gameLobby.numberOfRounds}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Column {
                        Text(
                            text = "Players",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${gameLobby.players.size}/${gameLobby.maxPlayers}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    if (!gameLobby.isPublic && gameLobby.passcode != null) {
                        Column {
                            Text(
                                text = "Passcode",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = gameLobby.passcode,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Players List
        Text(
            text = "Players",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(gameLobby.players) { player ->
                PlayerCard(
                    player = player,
                    isCurrentUser = player.userId == currentUser.userId
                )
            }
            
            // Show empty slots
            val emptySlots = gameLobby.maxPlayers - gameLobby.players.size
            if (emptySlots > 0) {
                items(emptySlots) {
                    EmptyPlayerSlot()
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Auto-start countdown display
        if (autoStartCountdown != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF9800).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔥 Lobby Full!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Game starting in ${autoStartCountdown}s",
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Action Buttons
        if (isHost && canStartGame && autoStartCountdown == null) {
            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Start Game",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else if ((!isHost || !canStartGame) && autoStartCountdown == null) {
            Button(
                onClick = onReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentPlayer?.isReady == true) Color.Gray else Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = currentPlayer?.isReady != true
            ) {
                if (currentPlayer?.isReady == true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Ready",
                            tint = Color.White
                        )
                        Text(
                            text = "Ready!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Text(
                        text = "Ready",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        
        if (isHost && !canStartGame && gameLobby.players.size > 1 && autoStartCountdown == null) {
            Text(
                text = "Waiting for all players to be ready...",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun PlayerCard(
    player: LobbyPlayer,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) Color(0xFF374151) else Color(0xFF1F2937)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Player Avatar
                Card(
                    modifier = Modifier.size(40.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (player.isReady) Color(0xFF10B981) else Color(0xFF6B7280)
                    ),
                    shape = CircleShape
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (player.isReady) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Ready",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Player",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // Player Info
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = player.username,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        
                        if (player.isHost) {
                            Text(
                                text = "👑",
                                fontSize = 16.sp
                            )
                        }
                        
                        if (isCurrentUser) {
                            Text(
                                text = "(You)",
                                fontSize = 12.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                    
                    Text(
                        text = if (player.isReady) "Ready" else "Not Ready",
                        fontSize = 12.sp,
                        color = if (player.isReady) Color(0xFF10B981) else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyPlayerSlot(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111827)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Empty Avatar
            Card(
                modifier = Modifier.size(40.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151)
                ),
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Empty Slot",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Text(
                text = "Waiting for player...",
                fontSize = 16.sp,
                color = Color(0xFF6B7280),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}
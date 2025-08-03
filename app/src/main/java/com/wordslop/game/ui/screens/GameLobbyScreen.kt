package com.wordslop.game.ui.screens

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
import com.wordslop.game.model.GameLobby
import com.wordslop.game.model.LobbyPlayer
import com.wordslop.game.auth.UserInfo

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
    
    // Main content: Two-column layout for landscape
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column (70% width) - Everything except player list
        Column(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight()
        ) {
            // Aligned headers row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // Match right column header height
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "Game Lobby",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Game info card (aligned with first player card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1F2937)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Game Settings",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Text(
                        text = if (gameLobby.isPublic) "Public" else "Private",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    
                    Row {
                        Text(
                            text = "Rounds: ",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${gameLobby.numberOfRounds}",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                    
                    if (!gameLobby.isPublic && gameLobby.passcode != null) {
                        Row {
                            Text(
                                text = "Passcode: ",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = gameLobby.passcode,
                                fontSize = 12.sp,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Auto-start countdown in left column
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
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Action Buttons in left column
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
        
        // Right Column (30% width) - ONLY Player List - FULL HEIGHT
        Column(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
        ) {
            // Aligned header with left column (same height as Game Lobby header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // Same height as IconButton + text on left
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Players",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${gameLobby.players.size}/${gameLobby.maxPlayers}",
                    fontSize = 12.sp,
                    color = if (gameLobby.players.size >= gameLobby.maxPlayers) Color(0xFFFF9800) else Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp)) // Same spacing as left column
            
            // Players list - aligned with Game Settings card
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                gameLobby.players.forEach { player ->
                    PlayerCard(
                        player = player,
                        isCurrentUser = player.userId == currentUser.userId
                    )
                }
                
                // Show empty slots
                val emptySlots = gameLobby.maxPlayers - gameLobby.players.size
                repeat(emptySlots) {
                    EmptyPlayerSlot()
                }
            }
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
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) Color(0xFF374151) else Color(0xFF1F2937)
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Name row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = player.username,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1
                    )
                    
                    if (player.isHost) {
                        Text(
                            text = "👑",
                            fontSize = 8.sp
                        )
                    }
                }
                
                // Ready status
                Text(
                    text = if (player.isReady) "✓" else "○",
                    fontSize = 10.sp,
                    color = if (player.isReady) Color(0xFF10B981) else Color.Gray
                )
            }
            
            // You indicator (if current user)
            if (isCurrentUser) {
                Text(
                    text = "(You)",
                    fontSize = 8.sp,
                    color = Color(0xFF10B981)
                )
            }
        }
    }
}

@Composable
fun EmptyPlayerSlot(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111827)
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Text(
                text = "Waiting...",
                fontSize = 10.sp,
                color = Color(0xFF6B7280),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
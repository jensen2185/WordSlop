package com.example.wordslop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.wordslop.ui.screens.MainMenuScreen
import com.example.wordslop.ui.screens.CreateGameScreen
import com.example.wordslop.ui.screens.GameLobbyScreen
import com.example.wordslop.ui.screens.WordGameScreen
import com.example.wordslop.model.GameLobby
import com.example.wordslop.model.LobbyPlayer
import com.example.wordslop.model.GameSettings
import com.example.wordslop.model.GameStatus
import com.example.wordslop.auth.UserInfo

sealed class Screen {
    object MainMenu : Screen()
    object JoinGame : Screen()
    object CreateGame : Screen()
    object GameLobby : Screen()
    object WordGame : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure status bar styling
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false // Dark status bar content
        
        setContent {
            MaterialTheme {
                WordslopApp()
            }
        }
    }
}

@Composable
fun WordslopApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.MainMenu) }
    var currentUser by remember { mutableStateOf<UserInfo?>(null) }
    var currentGameLobby by remember { mutableStateOf<GameLobby?>(null) }
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) { innerPadding ->
        when (currentScreen) {
            Screen.MainMenu -> {
                MainMenuScreen(
                    modifier = Modifier.padding(innerPadding),
                    onJoinGame = { user ->
                        currentUser = user
                        currentScreen = Screen.JoinGame
                    },
                    onCreateGame = { user ->
                        currentUser = user
                        currentScreen = Screen.CreateGame
                    },
                    onPracticeMode = { user ->
                        currentUser = user
                        currentScreen = Screen.WordGame
                    }
                )
            }
            
            Screen.JoinGame -> {
                // TODO: Implement JoinGameScreen
                PlaceholderScreen(
                    title = "Join Game",
                    subtitle = "Browse available games to join",
                    onBack = { currentScreen = Screen.MainMenu },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            
            Screen.CreateGame -> {
                currentUser?.let { user ->
                    CreateGameScreen(
                        userInfo = user,
                        onGameCreated = { gameSettings ->
                            // Create a new game lobby
                            val gameId = "game_${System.currentTimeMillis()}"
                            val hostPlayer = LobbyPlayer(
                                userId = user.userId,
                                username = user.gameUsername ?: user.displayName,
                                isReady = false,
                                isHost = true
                            )
                            
                            currentGameLobby = GameLobby(
                                gameId = gameId,
                                hostUserId = user.userId,
                                hostUsername = user.gameUsername ?: user.displayName,
                                isPublic = gameSettings.isPublic,
                                passcode = gameSettings.passcode,
                                maxPlayers = gameSettings.maxPlayers,
                                numberOfRounds = gameSettings.numberOfRounds,
                                players = listOf(hostPlayer),
                                gameStatus = GameStatus.WAITING
                            )
                            
                            currentScreen = Screen.GameLobby
                        },
                        onBack = { currentScreen = Screen.MainMenu },
                        modifier = Modifier.padding(innerPadding)
                    )
                } ?: run {
                    // User not logged in, go back to main menu
                    currentScreen = Screen.MainMenu
                }
            }
            
            Screen.GameLobby -> {
                currentGameLobby?.let { gameLobby ->
                    currentUser?.let { user ->
                        GameLobbyScreen(
                            gameLobby = gameLobby,
                            currentUser = user,
                            onBack = { 
                                currentGameLobby = null
                                currentScreen = Screen.MainMenu 
                            },
                            onReady = {
                                // Update player ready status
                                currentGameLobby = gameLobby.copy(
                                    players = gameLobby.players.map { player ->
                                        if (player.userId == user.userId) {
                                            player.copy(isReady = true)
                                        } else player
                                    }
                                )
                            },
                            onStartGame = {
                                currentScreen = Screen.WordGame
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                } ?: run {
                    // No game lobby, go back to main menu
                    currentScreen = Screen.MainMenu
                }
            }
            
            Screen.WordGame -> {
                WordGameScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = subtitle,
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "🚧 Coming Soon! 🚧",
            fontSize = 18.sp,
            color = Color(0xFF10B981),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Back to Main Menu")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WordslopAppPreview() {
    MaterialTheme {
        WordslopApp()
    }
}
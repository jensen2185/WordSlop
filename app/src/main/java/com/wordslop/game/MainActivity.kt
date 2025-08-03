package com.wordslop.game

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
import com.wordslop.game.ui.screens.MainMenuScreen
import com.wordslop.game.ui.screens.CreateGameScreen
import com.wordslop.game.ui.screens.GameLobbyScreen
import com.wordslop.game.ui.screens.WordGameScreen
import com.wordslop.game.ui.screens.JoinGameScreen
import com.wordslop.game.model.GameLobby
import com.wordslop.game.model.LobbyPlayer
import com.wordslop.game.model.GameSettings
import com.wordslop.game.model.GameStatus
import com.wordslop.game.model.GameMode
import com.wordslop.game.model.GamePlayer
import com.wordslop.game.auth.UserInfo

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
    var activePublicLobbies by remember { mutableStateOf<List<GameLobby>>(emptyList()) }
    
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
                        // Create a testing game lobby for practice mode
                        val practiceGameLobby = GameLobby(
                            gameId = "practice_${System.currentTimeMillis()}",
                            hostUserId = user.userId,
                            hostUsername = user.gameUsername ?: user.displayName,
                            isPublic = false,
                            maxPlayers = 6,
                            numberOfRounds = 3,
                            players = listOf(
                                LobbyPlayer(
                                    userId = user.userId,
                                    username = user.gameUsername ?: user.displayName,
                                    isReady = false,
                                    isHost = true
                                )
                            ),
                            gameStatus = GameStatus.IN_PROGRESS,
                            gameMode = GameMode.TESTING
                        )
                        currentGameLobby = practiceGameLobby
                        currentScreen = Screen.WordGame
                    }
                )
            }
            
            Screen.JoinGame -> {
                currentUser?.let { user ->
                    JoinGameScreen(
                        userInfo = user,
                        availableLobbies = activePublicLobbies.filter { it.isPublic && it.gameStatus == GameStatus.WAITING },
                        onBack = { currentScreen = Screen.MainMenu },
                        onJoinLobby = { selectedLobby ->
                            // Add current user to the selected lobby
                            val newPlayer = LobbyPlayer(
                                userId = user.userId,
                                username = user.gameUsername ?: user.displayName,
                                isReady = false,
                                isHost = false
                            )
                            
                            val updatedLobby = selectedLobby.copy(
                                players = selectedLobby.players + newPlayer
                            )
                            
                            // Update the lobby in the active lobbies list
                            activePublicLobbies = activePublicLobbies.map { lobby ->
                                if (lobby.gameId == selectedLobby.gameId) updatedLobby else lobby
                            }
                            
                            // Set as current lobby and go to lobby screen
                            currentGameLobby = updatedLobby
                            currentScreen = Screen.GameLobby
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                } ?: run {
                    // User not logged in, go back to main menu
                    currentScreen = Screen.MainMenu
                }
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
                            
                            val newLobby = GameLobby(
                                gameId = gameId,
                                hostUserId = user.userId,
                                hostUsername = user.gameUsername ?: user.displayName,
                                isPublic = gameSettings.isPublic,
                                passcode = gameSettings.passcode,
                                maxPlayers = gameSettings.maxPlayers,
                                numberOfRounds = gameSettings.numberOfRounds,
                                players = listOf(hostPlayer),
                                gameStatus = GameStatus.WAITING,
                                gameMode = gameSettings.gameMode
                            )
                            
                            // Add to active lobbies list if it's public
                            if (gameSettings.isPublic) {
                                activePublicLobbies = activePublicLobbies + newLobby
                            }
                            
                            currentGameLobby = newLobby
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
                                // Remove from active lobbies if leaving as host, or remove self if not host
                                currentGameLobby?.let { lobby ->
                                    if (lobby.hostUserId == user.userId) {
                                        // Host is leaving - remove entire lobby
                                        activePublicLobbies = activePublicLobbies.filter { it.gameId != lobby.gameId }
                                    } else {
                                        // Non-host leaving - remove player from lobby
                                        val updatedLobby = lobby.copy(
                                            players = lobby.players.filter { it.userId != user.userId }
                                        )
                                        activePublicLobbies = activePublicLobbies.map { 
                                            if (it.gameId == lobby.gameId) updatedLobby else it
                                        }
                                    }
                                }
                                currentGameLobby = null
                                currentScreen = Screen.MainMenu 
                            },
                            onReady = {
                                // Update player ready status
                                val updatedLobby = gameLobby.copy(
                                    players = gameLobby.players.map { player ->
                                        if (player.userId == user.userId) {
                                            player.copy(isReady = true)
                                        } else player
                                    }
                                )
                                
                                // Update both current lobby and active lobbies list
                                currentGameLobby = updatedLobby
                                if (gameLobby.isPublic) {
                                    activePublicLobbies = activePublicLobbies.map { lobby ->
                                        if (lobby.gameId == gameLobby.gameId) updatedLobby else lobby
                                    }
                                }
                            },
                            onStartGame = {
                                // Remove from active lobbies when game starts
                                currentGameLobby?.let { lobby ->
                                    activePublicLobbies = activePublicLobbies.filter { it.gameId != lobby.gameId }
                                }
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
                currentGameLobby?.let { gameLobby ->
                    currentUser?.let { user ->
                        // Convert lobby players to game players
                        val gamePlayers = gameLobby.players.map { lobbyPlayer ->
                            GamePlayer(
                                userId = lobbyPlayer.userId,
                                username = lobbyPlayer.username,
                                isReady = false,
                                selectedWords = emptyList(),
                                points = 0,
                                isCurrentUser = lobbyPlayer.userId == user.userId
                            )
                        }
                        
                        WordGameScreen(
                            gamePlayers = gamePlayers,
                            gameMode = gameLobby.gameMode,
                            onBackToLobby = if (gameLobby.gameMode == GameMode.ONLINE) {
                                {
                                    currentScreen = Screen.GameLobby
                                }
                            } else null,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                } ?: run {
                    // No game lobby, go back to main menu
                    currentScreen = Screen.MainMenu
                }
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
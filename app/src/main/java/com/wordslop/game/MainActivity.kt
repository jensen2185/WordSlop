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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import com.wordslop.game.auth.UserInfo
import com.wordslop.game.repository.LobbyRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.collect

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
    val lobbyRepository = remember { LobbyRepository() }
    val scope = rememberCoroutineScope()
    
    // Only listen to Firestore when user is authenticated
    var activePublicLobbies by remember { mutableStateOf<List<GameLobby>>(emptyList()) }
    
    // Set up Firebase listener only when user is authenticated
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            println("DEBUG MAIN: Setting up public lobbies flow for user ${user.userId}")
            lobbyRepository.getPublicLobbiesFlow().collect { lobbies ->
                activePublicLobbies = lobbies
            }
        } else {
            println("DEBUG MAIN: No user authenticated, clearing lobby list")
            activePublicLobbies = emptyList()
        }
    }
    
    // Clean up orphaned lobbies on app start and periodically (for all authenticated users)
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            // Clean up immediately on sign in
            lobbyRepository.cleanupOrphanedLobbies()
            
            // Then clean up every 15 seconds while app is running
            while (currentUser != null) {
                kotlinx.coroutines.delay(15000L) // 15 seconds
                lobbyRepository.cleanupOrphanedLobbies()
            }
        }
    }
    
    // Player heartbeat - keep active players' timestamps updated (only when actively in lobby screen)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(currentUser, currentGameLobby, currentScreen, lifecycleOwner) {
        val user = currentUser
        val lobby = currentGameLobby
        if (user != null && lobby != null && (currentScreen == Screen.GameLobby || currentScreen == Screen.WordGame)) {
            // Only send heartbeat when in STARTED state AND actually in lobby/game screen
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (currentGameLobby?.gameId == lobby.gameId && 
                       currentUser?.userId == user.userId &&
                       (currentScreen == Screen.GameLobby || currentScreen == Screen.WordGame)) {
                    println("DEBUG HEARTBEAT: Sending heartbeat for ${user.gameUsername} in lobby ${lobby.gameId} (screen: $currentScreen)")
                    lobbyRepository.updatePlayerHeartbeat(lobby.gameId, user.userId)
                    kotlinx.coroutines.delay(30000L) // 30 seconds
                }
            }
        }
    }
    
    // Manual lobby refresh when in lobby (simpler approach to avoid listener loops)
    currentGameLobby?.let { lobby ->
        val user = currentUser
        if (user != null) {
            LaunchedEffect(currentScreen, lobby.gameId) {
                if (currentScreen == Screen.GameLobby) {
                    while (currentScreen == Screen.GameLobby) {
                        kotlinx.coroutines.delay(2000L)
                        println("DEBUG: Manual lobby refresh check")
                        val result = lobbyRepository.getLobbyById(lobby.gameId)
                        result.onSuccess { fetchedLobby ->
                            if (fetchedLobby == null) {
                                // Lobby was deleted, go back to main menu
                                println("DEBUG: Lobby deleted, going to main menu")
                                currentGameLobby = null
                                currentScreen = Screen.MainMenu
                            } else {
                                // Update lobby state
                                currentGameLobby = fetchedLobby
                                
                                // Check if game started
                                if (fetchedLobby.gameStatus == GameStatus.IN_PROGRESS) {
                                    println("DEBUG: Game started, navigating to game screen")
                                    currentScreen = Screen.WordGame
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
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
                    },
                    onSignOut = {
                        // Clear all game state when signing out
                        currentUser = null
                        currentGameLobby = null
                        currentScreen = Screen.MainMenu
                        activePublicLobbies = emptyList()
                    }
                )
            }
            
            Screen.JoinGame -> {
                currentUser?.let { user ->
                    JoinGameScreen(
                        userInfo = user,
                        availableLobbies = activePublicLobbies.filter { it.isPublic && it.gameStatus == GameStatus.WAITING }.also { lobbies ->
                            println("DEBUG MAIN: JoinGameScreen receiving ${lobbies.size} available lobbies")
                            lobbies.forEach { lobby ->
                                println("DEBUG MAIN: - Lobby ${lobby.gameId}: ${lobby.hostUsername}, ${lobby.players.size}/${lobby.maxPlayers} players")
                            }
                        },
                        onBack = { currentScreen = Screen.MainMenu },
                        onJoinLobby = { selectedLobby ->
                            println("DEBUG JOIN: Attempting to join lobby ${selectedLobby.gameId}")
                            scope.launch {
                                try {
                                    val newPlayer = LobbyPlayer(
                                        userId = user.userId,
                                        username = user.gameUsername ?: user.displayName,
                                        isReady = false,
                                        isHost = false
                                    )
                                    
                                    println("DEBUG JOIN: Created player: ${newPlayer.username} (${newPlayer.userId})")
                                    
                                    val result = lobbyRepository.joinLobby(selectedLobby.gameId, newPlayer)
                                    result.onSuccess {
                                        println("DEBUG JOIN: Successfully joined lobby!")
                                        // Set the lobby immediately for navigation, real-time updates will sync the actual state
                                        currentGameLobby = selectedLobby.copy(
                                            players = selectedLobby.players + newPlayer
                                        )
                                        currentScreen = Screen.GameLobby
                                    }.onFailure { error ->
                                        // Handle error - could show a toast/dialog
                                        println("DEBUG JOIN ERROR: Failed to join lobby: ${error.message}")
                                        error.printStackTrace()
                                    }
                                } catch (e: Exception) {
                                    println("DEBUG JOIN EXCEPTION: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
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
                            scope.launch {
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
                                    gameStatus = GameStatus.WAITING
                                )
                                
                                val result = lobbyRepository.createLobby(newLobby)
                                result.onSuccess {
                                    currentGameLobby = newLobby
                                    currentScreen = Screen.GameLobby
                                }.onFailure { error ->
                                    // Handle error - could show a toast/dialog
                                    println("Failed to create lobby: ${error.message}")
                                }
                            }
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
                                scope.launch {
                                    val result = lobbyRepository.leaveLobby(gameLobby.gameId, user.userId)
                                    result.onSuccess {
                                        currentGameLobby = null
                                        currentScreen = Screen.MainMenu
                                    }.onFailure { error ->
                                        // Handle error - could show a toast/dialog
                                        println("Failed to leave lobby: ${error.message}")
                                        // Still navigate back on error
                                        currentGameLobby = null
                                        currentScreen = Screen.MainMenu
                                    }
                                }
                            },
                            onReady = {
                                scope.launch {
                                    val result = lobbyRepository.updatePlayerReady(gameLobby.gameId, user.userId, true)
                                    result.onFailure { error ->
                                        // Handle error - could show a toast/dialog
                                        println("Failed to update ready status: ${error.message}")
                                    }
                                }
                            },
                            onStartGame = {
                                scope.launch {
                                    println("DEBUG: Host starting game for lobby ${gameLobby.gameId}")
                                    val result = lobbyRepository.updateLobbyStatus(gameLobby.gameId, GameStatus.IN_PROGRESS)
                                    result.onSuccess {
                                        println("DEBUG: Host successfully updated lobby status, navigating to game")
                                        currentScreen = Screen.WordGame
                                    }.onFailure { error ->
                                        // Handle error - could show a toast/dialog
                                        println("DEBUG: Host failed to start game: ${error.message}")
                                    }
                                }
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
                    modifier = Modifier.padding(innerPadding),
                    gameLobby = currentGameLobby, // Pass lobby for multiplayer context
                    currentUser = currentUser,
                    onBackToMainMenu = {
                        currentGameLobby = null
                        currentScreen = Screen.MainMenu
                    }
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
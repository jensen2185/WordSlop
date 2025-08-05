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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
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
    var joinErrorMessage by remember { mutableStateOf<String?>(null) }
    
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
    
    // User presence heartbeat system for online tracking
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            println("DEBUG PRESENCE: Starting heartbeat for user ${user.userId}")
            
            // Initial presence update
            lobbyRepository.updateUserPresence(user.userId, user.gameUsername ?: user.displayName)
            
            // Heartbeat every 30 seconds
            while (true) {
                kotlinx.coroutines.delay(30000L) // 30 seconds
                val result = lobbyRepository.updateUserPresence(user.userId, user.gameUsername ?: user.displayName)
                result.onFailure { error ->
                    println("DEBUG PRESENCE: Failed to update heartbeat: ${error.message}")
                }
            }
        }
    }
    
    // Clean up presence when user logs out
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user == null) {
            // User logged out - clean up any presence data
            // This will be handled by the cleanup logic in the presence tracking
            println("DEBUG PRESENCE: User logged out, presence will be cleaned up automatically")
        }
    }
    
    // Clean up orphaned lobbies on app start and periodically (for all authenticated users)
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            // Clean up immediately on sign in
            lobbyRepository.cleanupOrphanedLobbies()
            
            // Then clean up periodically while app is running
            // More frequent cleanup during active games for disconnect detection
            while (currentUser != null) {
                // Always use 5 second cleanup for better disconnect detection
                val cleanupInterval = 5000L
                
                kotlinx.coroutines.delay(cleanupInterval)
                println("DEBUG: Running cleanup (interval: ${cleanupInterval}ms, screen: $currentScreen)")
                val cleanupResult = lobbyRepository.cleanupOrphanedLobbies()
                cleanupResult.onFailure { e ->
                    println("DEBUG: Cleanup failed: ${e.message}")
                }
            }
        }
    }
    
    // Handle app lifecycle - leave lobby when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(currentUser, currentGameLobby, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // App going to background, leave the lobby
                    val user = currentUser
                    val lobby = currentGameLobby
                    if (user != null && lobby != null && (currentScreen == Screen.GameLobby || currentScreen == Screen.WordGame)) {
                        println("DEBUG LIFECYCLE: App paused, leaving lobby ${lobby.gameId}")
                        scope.launch {
                            lobbyRepository.leaveLobby(lobby.gameId, user.userId)
                        }
                    }
                    
                    // Clean up user presence
                    if (user != null) {
                        println("DEBUG PRESENCE: App paused, cleaning up presence for ${user.userId}")
                        scope.launch {
                            lobbyRepository.removeUserPresence(user.userId)
                        }
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // App resumed, update presence
                    val user = currentUser
                    if (user != null) {
                        println("DEBUG PRESENCE: App resumed, updating presence for ${user.userId}")
                        scope.launch {
                            lobbyRepository.updateUserPresence(user.userId, user.gameUsername ?: user.displayName)
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    // Additional safety for when app is stopped
                    val user = currentUser
                    val lobby = currentGameLobby
                    if (user != null && lobby != null) {
                        println("DEBUG LIFECYCLE: App stopped, ensuring left lobby ${lobby.gameId}")
                        scope.launch {
                            lobbyRepository.leaveLobby(lobby.gameId, user.userId)
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Player heartbeat - keep active players' timestamps updated (only when actively in lobby screen)
    LaunchedEffect(currentUser, currentGameLobby, currentScreen, lifecycleOwner) {
        val user = currentUser
        val lobby = currentGameLobby
        if (user != null && lobby != null && (currentScreen == Screen.GameLobby || currentScreen == Screen.WordGame)) {
            // Only send heartbeat when in STARTED state AND actually in lobby/game screen
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (currentGameLobby?.gameId == lobby.gameId && 
                       currentUser?.userId == user.userId &&
                       (currentScreen == Screen.GameLobby || currentScreen == Screen.WordGame)) {
                    
                    // Faster heartbeat during active gameplay for disconnect detection
                    val heartbeatInterval = when (currentScreen) {
                        Screen.WordGame -> 3000L  // 3 seconds during gameplay
                        Screen.GameLobby -> 10000L // 10 seconds in lobby
                        else -> 30000L
                    }
                    
                    println("DEBUG HEARTBEAT: Sending heartbeat for ${user.gameUsername} in lobby ${lobby.gameId} (screen: $currentScreen, interval: ${heartbeatInterval}ms)")
                    lobbyRepository.updatePlayerHeartbeat(lobby.gameId, user.userId)
                    kotlinx.coroutines.delay(heartbeatInterval)
                }
            }
        }
    }
    
    // Real-time lobby observation when in lobby or game
    currentGameLobby?.let { lobby ->
        val user = currentUser
        if (user != null) {
            LaunchedEffect(currentScreen, lobby.gameId) {
                if (currentScreen == Screen.GameLobby || currentScreen == Screen.WordGame) {
                    println("DEBUG: Starting real-time lobby observation for ${lobby.gameId}")
                    lobbyRepository.getLobbyFlow(lobby.gameId).collect { fetchedLobby ->
                        if (fetchedLobby == null) {
                            // Lobby was deleted or user removed, go back to main menu
                            println("DEBUG: Lobby deleted or unavailable, going to main menu")
                            currentGameLobby = null
                            currentScreen = Screen.MainMenu
                        } else {
                            // Check if current user is still in the lobby
                            val stillInLobby = fetchedLobby.players.any { it.userId == user.userId }
                            if (!stillInLobby && currentScreen != Screen.MainMenu) {
                                println("DEBUG: Current user removed from lobby, going to main menu")
                                currentGameLobby = null
                                currentScreen = Screen.MainMenu
                            } else {
                                // Update lobby state with real-time data
                                val oldPlayerCount = currentGameLobby?.players?.size ?: 0
                                val newPlayerCount = fetchedLobby.players.size
                                
                                if (oldPlayerCount != newPlayerCount) {
                                    println("DEBUG: Player count changed from $oldPlayerCount to $newPlayerCount")
                                    fetchedLobby.players.forEach { player ->
                                        println("DEBUG: - ${player.username} (ready: ${player.isReady}, host: ${player.isHost})")
                                    }
                                }
                                
                                currentGameLobby = fetchedLobby
                                
                                // Check if game started while in lobby
                                if (currentScreen == Screen.GameLobby && fetchedLobby.gameStatus == GameStatus.IN_PROGRESS) {
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
                        availableLobbies = activePublicLobbies.filter { it.isPublic && (it.gameStatus == GameStatus.WAITING || it.gameStatus == GameStatus.IN_PROGRESS) }.also { lobbies ->
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
                                        isHost = false,
                                        isSpectator = selectedLobby.gameStatus == GameStatus.IN_PROGRESS
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
                                        // Handle error - show user-visible message
                                        println("DEBUG JOIN ERROR: Failed to join lobby: ${error.message}")
                                        error.printStackTrace()
                                        joinErrorMessage = "Failed to join game: ${error.message}"
                                    }
                                } catch (e: Exception) {
                                    println("DEBUG JOIN EXCEPTION: ${e.message}")
                                    e.printStackTrace()
                                    joinErrorMessage = "Failed to join game: ${e.message}"
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
    
    // Show join error dialog if there's an error
    joinErrorMessage?.let { errorMessage ->
        LaunchedEffect(errorMessage) {
            // Auto-dismiss after 3 seconds
            kotlinx.coroutines.delay(3000L)
            joinErrorMessage = null
        }
        
        // Simple error display - could be enhanced with a proper dialog
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.9f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Join Error",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { joinErrorMessage = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        )
                    ) {
                        Text("OK", color = Color.Red)
                    }
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
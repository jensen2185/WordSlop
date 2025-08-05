package com.wordslop.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordslop.game.model.Word
import com.wordslop.game.repository.WordRepository
import com.wordslop.game.ui.components.WordCard
import com.wordslop.game.ui.components.DraggableWordCard
import com.wordslop.game.ui.components.ArrangementBar
import com.wordslop.game.ui.components.SelfVoteWarningDialog
import com.wordslop.game.ui.components.EmojiSelectionDialog
import com.wordslop.game.model.GameLobby
import com.wordslop.game.model.GameStatus
import com.wordslop.game.auth.UserInfo
import com.wordslop.game.repository.LobbyRepository
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class Player(
    val name: String,
    val isReady: Boolean = false,
    val selectedWords: List<String> = emptyList(),
    val points: Int = 0,
    val currentRoundPoints: Int = 0
)

/**
 * Smart join function that handles special spacing for add-on words.
 * - "s" and "'s" can join with previous regular words or "er"/"es"
 * - "er" and "es" can join with previous regular words and allow subsequent add-ons to join with them
 * - "as" acts as a blocker - prevents add-ons from joining with it and doesn't join with previous words
 */
fun smartJoinWords(words: List<String>): String {
    if (words.isEmpty()) return ""
    if (words.size == 1) return words[0]
    
    val joinableAddOns = setOf("s", "'s", "er", "es")  // These can join with previous words
    val blockingWords = setOf("as")  // These prevent add-ons from joining but don't join themselves
    val joinableWithWords = setOf("er", "es")  // These allow subsequent add-ons to join with them
    val result = StringBuilder()
    
    for (i in words.indices) {
        val currentWord = words[i]
        
        if (i == 0) {
            // First word always gets added as-is
            result.append(currentWord)
        } else {
            val previousWord = words[i - 1]
            val currentIsJoinableAddOn = currentWord in joinableAddOns
            val previousIsBlocker = previousWord in blockingWords
            val previousIsNonJoinableAddOn = previousWord in joinableAddOns && previousWord !in joinableWithWords
            
            if (currentIsJoinableAddOn && !previousIsBlocker && !previousIsNonJoinableAddOn) {
                // Current word is a joinable add-on following a regular word or "er" - no space
                result.append(currentWord)
            } else {
                // All other cases - add space before current word
                result.append(" ").append(currentWord)
            }
        }
    }
    
    return result.toString()
}

enum class GamePhase {
    PLAYING, VOTING, RESULTS, WINNER
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordGameScreen(
    modifier: Modifier = Modifier,
    gameLobby: GameLobby? = null, // If null, it's practice mode
    currentUser: UserInfo? = null,
    onBackToMainMenu: (() -> Unit)? = null
) {
    val wordRepository = remember { WordRepository() }
    val lobbyRepository = if (gameLobby != null && currentUser != null) remember { LobbyRepository() } else null
    val scope = rememberCoroutineScope()
    var gameWords by remember { mutableStateOf(wordRepository.getRandomWords()) }
    var specialWords by remember { mutableStateOf(wordRepository.getSpecialWords()) }
    // Simple list of words in order
    var arrangedWords by remember { mutableStateOf<List<Word>>(emptyList()) }
    var draggedWordId by remember { mutableStateOf<Int?>(null) }
    var topBarGlobalX by remember { mutableStateOf(0f) }
    var topBarWidth by remember { mutableStateOf(0f) }
    var wordBankDragPosition by remember { mutableStateOf(0f) }
    var isDraggingFromWordBank by remember { mutableStateOf(false) }
    var actualWordPositions by remember { mutableStateOf(mapOf<Int, Float>()) }
    
    // Timer and player state
    var timeLeft by remember { mutableStateOf(60) }
    var gamePhase by remember { mutableStateOf(GamePhase.PLAYING) }
    var votingTimeLeft by remember { mutableStateOf(20) }
    var resultsTimeLeft by remember { mutableStateOf(5) }
    var userVote by remember { mutableStateOf<Int?>(null) }
    var currentRound by remember { mutableStateOf(1) }
    var totalRounds by remember { mutableStateOf(gameLobby?.numberOfRounds ?: 3) }
    var showSelfVoteWarning by remember { mutableStateOf(false) }
    var showEmojiDialog by remember { mutableStateOf(false) }
    var selectedSentenceForEmoji by remember { mutableStateOf<Int?>(null) }
    var sentenceEmojis by remember { mutableStateOf<Map<Int, List<String>>>(emptyMap()) }
    
    // Check if current user is a spectator (updated reactively)
    var isSpectator by remember { 
        val initialSpectatorStatus = gameLobby?.players?.find { it.userId == currentUser?.userId }?.isSpectator == true
        println("DEBUG SPECTATOR: INITIAL spectator status = $initialSpectatorStatus for user ${currentUser?.userId}")
        mutableStateOf(initialSpectatorStatus)
    }
    
    // Update spectator status when lobby changes
    LaunchedEffect(gameLobby?.players) {
        gameLobby?.let { lobby ->
            val player = lobby.players.find { it.userId == currentUser?.userId }
            val wasSpectator = isSpectator
            isSpectator = player?.isSpectator == true
            println("DEBUG SPECTATOR: Updated spectator status from $wasSpectator to $isSpectator for user ${currentUser?.userId}")
            println("DEBUG SPECTATOR: Player data: ${player?.toString()}")
            println("DEBUG SPECTATOR: Lobby has ${lobby.players.size} players: ${lobby.players.map { "${it.username}(spectator=${it.isSpectator})" }}")
            if (wasSpectator && !isSpectator) {
                println("DEBUG SPECTATOR: PROMOTED FROM SPECTATOR TO PLAYER! Current phase: $gamePhase, round: $currentRound")
            }
        }
    }
    
    // Initialize players based on mode (multiplayer vs practice)
    var players by remember { 
        mutableStateOf(
            if (gameLobby != null) {
                // Multiplayer mode - use real players from lobby
                // IMPORTANT: Reset ready status for game phase (separate from lobby ready)
                gameLobby.players.map { lobbyPlayer ->
                    Player(
                        name = if (lobbyPlayer.userId == currentUser?.userId) "You" else lobbyPlayer.username,
                        isReady = false, // Always start as not ready in game
                        selectedWords = emptyList()
                    )
                }
            } else {
                // Practice mode - use CPU players
                listOf(
                    Player("You", false, emptyList()),
                    Player("CPU1", false, emptyList()),
                    Player("CPU2", false, emptyList()),
                    Player("CPU3", false, emptyList()),
                    Player("CPU4", false, emptyList()),
                    Player("CPU5", false, emptyList())
                )
            }
        ) 
    }
    
    // Helper function for word insertion
    val onWordInsertAt: (Word, Float) -> Unit = { word, relativeX ->
        if (arrangedWords.isEmpty()) {
            arrangedWords = listOf(word.copy(isPlaced = true))
            // Update appropriate word list
            if (gameWords.any { it.id == word.id }) {
                gameWords = gameWords.map {
                    if (it.id == word.id) it.copy(isPlaced = true) else it
                }
            } else {
                specialWords = specialWords.map {
                    if (it.id == word.id) it.copy(isPlaced = true) else it
                }
            }
        } else {
            var wordsToMyLeft = 0
            for (i in arrangedWords.indices) {
                val wordPosition = actualWordPositions[i]
                if (wordPosition != null && relativeX > wordPosition) {
                    wordsToMyLeft++
                }
            }
            val insertionIndex = wordsToMyLeft.coerceIn(0, arrangedWords.size)
            val mutableList = arrangedWords.toMutableList()
            mutableList.add(insertionIndex, word.copy(isPlaced = true))
            arrangedWords = mutableList
            
            // Update appropriate word list
            if (gameWords.any { it.id == word.id }) {
                gameWords = gameWords.map {
                    if (it.id == word.id) it.copy(isPlaced = true) else it
                }
            } else {
                specialWords = specialWords.map {
                    if (it.id == word.id) it.copy(isPlaced = true) else it
                }
            }
        }
    }
    
    val configuration = LocalConfiguration.current
    
    // Determine grid columns based on screen orientation  
    val gridColumns = if (configuration.screenWidthDp > configuration.screenHeightDp) {
        // Landscape mode - more columns for horizontal optimization
        6
    } else {
        // Portrait mode - fewer columns
        4
    }
    
    // Timer countdown effect for playing phase (disabled for spectators)
    LaunchedEffect(gamePhase, isSpectator) {
        if (gamePhase == GamePhase.PLAYING && !isSpectator) {
            while (timeLeft > 0 && gamePhase == GamePhase.PLAYING) {
                kotlinx.coroutines.delay(1000L)
                timeLeft--
                
                // Check if all non-spectator players are ready (in practice mode, only check non-spectator players)
                val nonSpectatorPlayers = if (gameLobby != null) {
                    players.filterIndexed { index, _ ->
                        gameLobby.players.getOrNull(index)?.isSpectator == false
                    }
                } else {
                    players.filter { it.name != "You" || !isSpectator }
                }
                
                if (nonSpectatorPlayers.all { it.isReady }) {
                    gamePhase = GamePhase.VOTING
                    break
                }
            }
            
            if (timeLeft <= 0) {
                gamePhase = GamePhase.VOTING
            }
        }
    }
    
    // Voting timer countdown effect (disabled for spectators)
    LaunchedEffect(gamePhase, isSpectator) {
        if (gamePhase == GamePhase.VOTING && !isSpectator) {
            while (votingTimeLeft > 0 && gamePhase == GamePhase.VOTING) {
                kotlinx.coroutines.delay(1000L)
                votingTimeLeft--
                
                // In multiplayer mode, check if all players have voted
                if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                    val votingResult = lobbyRepository.getVotingResults(gameLobby.gameId)
                    votingResult.onSuccess { votes ->
                        // Check if all non-spectator players have voted
                        val nonSpectatorCount = gameLobby.players.count { !it.isSpectator }
                        if (votes.size >= nonSpectatorCount) {
                            gamePhase = GamePhase.RESULTS
                            return@LaunchedEffect
                        }
                    }
                }
            }
            
            // Move to results when time runs out
            if (votingTimeLeft <= 0) {
                println("DEBUG: Voting timer expired, moving to results. Round: $currentRound")
                gamePhase = GamePhase.RESULTS
            }
        }
    }
    
    // Results timer countdown effect (disabled for spectators)
    LaunchedEffect(gamePhase, isSpectator) {
        if (gamePhase == GamePhase.RESULTS && !isSpectator) {
            while (resultsTimeLeft > 0 && gamePhase == GamePhase.RESULTS) {
                kotlinx.coroutines.delay(1000L)
                resultsTimeLeft--
            }
            
            // Auto-proceed after 5 seconds
            if (resultsTimeLeft <= 0) {
                if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                    // Multiplayer mode - only host controls round transitions
                    val isHost = gameLobby.players.find { it.userId == currentUser.userId }?.isHost == true
                    if (isHost) {
                        // Host sends signals to non-hosts but doesn't advance locally here
                        // (local advancement happens in the round signal monitoring section)
                        if (currentRound < totalRounds) {
                            scope.launch {
                                println("DEBUG HOST: Host signaling next round ${currentRound + 1}")
                                // Clear old voting data first
                                lobbyRepository.clearVotingData(gameLobby.gameId)
                                
                                // Promote spectators to regular players at the start of new round
                                println("DEBUG HOST: Promoting spectators to players for round ${currentRound + 1}")
                                val promotionResult = lobbyRepository.promoteSpectatorsToPlayers(gameLobby.gameId)
                                promotionResult.onSuccess {
                                    println("DEBUG HOST: Successfully promoted spectators")
                                }.onFailure { error ->
                                    println("DEBUG HOST: Failed to promote spectators: ${error.message}")
                                }
                                
                                // Signal next round to non-host players
                                val roundSignal = mapOf(
                                    "action" to "next_round",
                                    "round" to (currentRound + 1),
                                    "timestamp" to System.currentTimeMillis()
                                )
                                
                                try {
                                    FirebaseFirestore.getInstance().collection("round_signals").document(gameLobby.gameId).set(roundSignal).await()
                                    println("DEBUG HOST: Successfully sent next round signal for round ${currentRound + 1} at timestamp ${roundSignal["timestamp"]}")
                                } catch (e: Exception) {
                                    println("DEBUG HOST: Failed to send signal: ${e.message}")
                                    // Fallback: advance locally if Firebase fails
                                    currentRound++
                                    gameWords = wordRepository.getRandomWords()
                                    specialWords = wordRepository.getSpecialWords()
                                    arrangedWords = emptyList()
                                    timeLeft = 60
                                    votingTimeLeft = 20
                                    resultsTimeLeft = 5
                                    gamePhase = GamePhase.PLAYING
                                    userVote = null
                                    sentenceEmojis = emptyMap()
                                    players = players.map { 
                                        it.copy(
                                            isReady = false, 
                                            selectedWords = emptyList(),
                                            points = it.points + it.currentRoundPoints,
                                            currentRoundPoints = 0
                                        )
                                    }
                                }
                            }
                        } else {
                            scope.launch {
                                println("DEBUG HOST: Host signaling game end")
                                val roundSignal = mapOf(
                                    "action" to "end_game",
                                    "timestamp" to System.currentTimeMillis()
                                )
                                
                                try {
                                    FirebaseFirestore.getInstance().collection("round_signals").document(gameLobby.gameId).set(roundSignal).await()
                                    println("DEBUG HOST: Successfully sent game end signal")
                                } catch (e: Exception) {
                                    println("DEBUG HOST: Failed to send signal: ${e.message}")
                                    // Fallback: end game locally if Firebase fails
                                    // Accumulate final round points first
                                    players = players.map { 
                                        it.copy(
                                            points = it.points + it.currentRoundPoints,
                                            currentRoundPoints = 0
                                        )
                                    }
                                    // Update lobby status to FINISHED when game ends (host fallback)
                                    scope.launch {
                                        lobbyRepository.updateLobbyStatus(gameLobby.gameId, GameStatus.FINISHED)
                                    }
                                    gamePhase = GamePhase.WINNER
                                }
                            }
                        }
                    }
                    // Non-host players wait for lobby status changes to sync their state (handled by lobby status monitoring)
                } else {
                    // Practice mode - local round advancement
                    if (currentRound < totalRounds) {
                        // Go to next round - simple approach
                        currentRound++
                        gameWords = wordRepository.getRandomWords()
                        specialWords = wordRepository.getSpecialWords()
                        arrangedWords = emptyList()
                        timeLeft = 60
                        votingTimeLeft = 20
                        resultsTimeLeft = 5
                        gamePhase = GamePhase.PLAYING
                        userVote = null
                        sentenceEmojis = emptyMap()
                        players = players.map { 
                            it.copy(
                                isReady = false, 
                                selectedWords = emptyList(),
                                points = it.points + it.currentRoundPoints,
                                currentRoundPoints = 0
                            )
                        }
                    } else {
                        // Game finished - accumulate final round points and show winner
                        players = players.map { 
                            it.copy(
                                points = it.points + it.currentRoundPoints,
                                currentRoundPoints = 0
                            )
                        }
                        gamePhase = GamePhase.WINNER
                    }
                }
            }
        }
    }
    
    // Round signal monitoring for multiplayer round transitions (both host and non-host players)
    if (gameLobby != null && currentUser != null && lobbyRepository != null) {
        val isHost = gameLobby.players.find { it.userId == currentUser.userId }?.isHost == true
        
        // Both host and non-host players listen for round signals (host listens to its own signals)
        var lastProcessedSignal by remember { mutableStateOf<Long>(0L) }
        
        LaunchedEffect(gameLobby.gameId) {
            println("DEBUG SIGNAL: Setting up round signal listener for lobby ${gameLobby.gameId}")
            
            // Monitor round signals from host
            FirebaseFirestore.getInstance().collection("round_signals").document(gameLobby.gameId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        println("DEBUG SIGNAL: Error listening for round signals: ${error.message}")
                        return@addSnapshotListener
                    }
                    
                    val playerType = if (isHost) "HOST" else "NON-HOST"
                    println("DEBUG SIGNAL $playerType: Snapshot received - exists: ${snapshot?.exists()}")
                    
                    if (snapshot?.exists() == true) {
                        val data = snapshot.data
                        println("DEBUG SIGNAL $playerType: Document data: $data")
                        
                        val action = snapshot.getString("action")
                        val round = snapshot.getLong("round")?.toInt()
                        val timestamp = snapshot.getLong("timestamp") ?: 0L
                        
                        println("DEBUG SIGNAL $playerType: action=$action, round=$round, timestamp=$timestamp, lastProcessed=$lastProcessedSignal")
                        println("DEBUG SIGNAL $playerType: currentPhase=$gamePhase, currentRound=$currentRound")
                        
                        // Only process each signal once (avoid duplicates)
                        if (timestamp > lastProcessedSignal) {
                            lastProcessedSignal = timestamp
                            println("DEBUG SIGNAL $playerType: Processing signal...")
                            
                            when (action) {
                                "next_round" -> {
                                    if (round != null) {
                                        println("DEBUG SIGNAL $playerType: FORCING advance to next round ($round) - isSpectator: $isSpectator, currentPhase: $gamePhase")
                                        
                                        // Advance to next round
                                        currentRound = round
                                        gameWords = wordRepository.getRandomWords()
                                        specialWords = wordRepository.getSpecialWords()
                                        arrangedWords = emptyList()
                                        timeLeft = 60
                                        votingTimeLeft = 20
                                        resultsTimeLeft = 5
                                        gamePhase = GamePhase.PLAYING
                                        userVote = null
                                        sentenceEmojis = emptyMap()
                                        players = players.map { 
                                            val newTotalPoints = it.points + it.currentRoundPoints
                                            println("DEBUG ROUND: Player ${it.name} - old points: ${it.points}, round points: ${it.currentRoundPoints}, new total: $newTotalPoints")
                                            it.copy(
                                                isReady = false, 
                                                selectedWords = emptyList(),
                                                points = newTotalPoints,
                                                currentRoundPoints = 0
                                            )
                                        }
                                        println("DEBUG SIGNAL $playerType: Successfully FORCED advance to round $round")
                                    } else {
                                        println("DEBUG SIGNAL $playerType: Skipping next_round - null round")
                                    }
                                }
                                "end_game" -> {
                                    if (gamePhase == GamePhase.RESULTS) {
                                        println("DEBUG SIGNAL $playerType: Ending game")
                                        // Accumulate final round points first
                                        players = players.map { 
                                            it.copy(
                                                points = it.points + it.currentRoundPoints,
                                                currentRoundPoints = 0
                                            )
                                        }
                                        // Update lobby status to FINISHED when game ends (signal processing)
                                        scope.launch {
                                            lobbyRepository.updateLobbyStatus(gameLobby.gameId, GameStatus.FINISHED)
                                        }
                                        gamePhase = GamePhase.WINNER
                                        println("DEBUG SIGNAL $playerType: Successfully ended game")
                                    } else {
                                        println("DEBUG SIGNAL $playerType: Skipping end_game - wrong phase ($gamePhase)")
                                    }
                                }
                                else -> {
                                    println("DEBUG SIGNAL $playerType: Unknown action: $action")
                                }
                            }
                        } else {
                            println("DEBUG SIGNAL $playerType: Skipping signal - already processed (timestamp=$timestamp <= lastProcessed=$lastProcessedSignal)")
                        }
                    } else {
                        println("DEBUG SIGNAL $playerType: Document does not exist")
                    }
                }
        }
    }
    
    // CPU simulation effect (only in practice mode)
    LaunchedEffect(gamePhase) {
        if (gamePhase == GamePhase.PLAYING && gameLobby == null) {
            // Only simulate CPU players in practice mode
            kotlinx.coroutines.delay(3000L) // Wait 3 seconds, then all CPUs get ready
            
            players = players.map { player ->
                if (player.name.startsWith("CPU")) {
                    player.copy(
                        isReady = true,
                        selectedWords = gameWords.shuffled().take(3).map { word -> word.text }
                    )
                } else player
            }
        }
    }
    
    // CPU voting simulation (only in practice mode)
    LaunchedEffect(gamePhase, userVote) {
        if (gamePhase == GamePhase.VOTING && gameLobby == null && userVote != null) {
            // Wait a moment for user to see their vote, then CPUs vote
            kotlinx.coroutines.delay(1000L) // 1 second delay
            
            // All CPU players vote for the user's sentence (index 0, which is "You")
            val userSentenceIndex = 0 // "You" is always first in the voting list
            
            // Update players to give the user points from CPU votes
            players = players.map { player ->
                if (player.name == "You") {
                    // User gets 1 point for each CPU vote (5 CPUs = 5 points)
                    player.copy(currentRoundPoints = player.currentRoundPoints + 5)
                } else player
            }
            
            // Immediately proceed to results since all have "voted"
            gamePhase = GamePhase.RESULTS
        }
    }
    
    // Multiplayer ready status sync (only in multiplayer mode)
    if (gameLobby != null && currentUser != null && lobbyRepository != null) {
        val updatedLobby by lobbyRepository.getLobbyFlow(gameLobby.gameId).collectAsState(initial = gameLobby)
        
        // Periodic sync for ready states during playing phase (every 2 seconds) - disabled for spectators
        LaunchedEffect(gamePhase, isSpectator) {
            if (gamePhase == GamePhase.PLAYING && !isSpectator) {
                while (gamePhase == GamePhase.PLAYING) {
                    kotlinx.coroutines.delay(2000L)
                    println("DEBUG GAME: Periodic sync - checking ready states")
                    
                    // Get game-specific ready states from Firestore
                    val gameReadyResult = lobbyRepository.getGameReadyStates(gameLobby.gameId)
                    gameReadyResult.onSuccess { gameReadyStates ->
                        println("DEBUG GAME: Got ${gameReadyStates.size} ready states from Firestore")
                        
                        // Update player ready status from game ready states (not lobby ready states)
                        val updatedPlayers = players.map { localPlayer ->
                            val currentUserId = if (localPlayer.name == "You") currentUser.userId else {
                                // Find userId by matching username
                                gameLobby.players.find { it.username == localPlayer.name }?.userId
                            }
                            
                            val gameState = currentUserId?.let { gameReadyStates[it] }
                            if (gameState != null) {
                                val newReadyStatus = gameState["isReady"] as? Boolean ?: false
                                val newWords = (gameState["selectedWords"] as? List<String>) ?: emptyList()
                                
                                // Log status changes
                                if (localPlayer.isReady != newReadyStatus) {
                                    println("DEBUG GAME: ${localPlayer.name} ready status changed: ${localPlayer.isReady} -> $newReadyStatus")
                                }
                                
                                localPlayer.copy(
                                    isReady = newReadyStatus,
                                    selectedWords = newWords,
                                    points = localPlayer.points, // Preserve existing points
                                    currentRoundPoints = localPlayer.currentRoundPoints // Preserve current round points
                                )
                            } else {
                                localPlayer
                            }
                        }
                        
                        // Only update if there are actual changes
                        if (updatedPlayers != players) {
                            players = updatedPlayers
                        }
                        
                        // Check if all non-spectator players are ready for voting
                        val nonSpectatorPlayerIds = gameLobby.players.filter { !it.isSpectator }.map { it.userId }
                        val nonSpectatorReadyStates = gameReadyStates.filterKeys { it in nonSpectatorPlayerIds }
                        val allNonSpectatorsReady = nonSpectatorReadyStates.values.all { 
                            it["isReady"] as? Boolean ?: false 
                        }
                        if (allNonSpectatorsReady && nonSpectatorReadyStates.size >= 2) {
                            println("DEBUG GAME: All non-spectator players ready, proceeding to voting")
                            gamePhase = GamePhase.VOTING
                            return@LaunchedEffect
                        }
                    }.onFailure { error ->
                        println("DEBUG GAME: Failed to sync ready states: ${error.message}")
                    }
                }
            }
        }
    }
    
    // Real-time lobby observation to update players list when someone disconnects
    if (gameLobby != null && lobbyRepository != null) {
        LaunchedEffect(gameLobby.gameId) {
            println("DEBUG GAME: Starting real-time lobby observation for player updates")
            lobbyRepository.getLobbyFlow(gameLobby.gameId).collect { updatedLobby ->
                if (updatedLobby != null) {
                    // Update players list based on who's still in the lobby
                    val updatedPlayers = updatedLobby.players.map { lobbyPlayer ->
                        // Find existing player to preserve their game state (isReady, points, etc.)
                        val existingPlayer = players.find { 
                            (lobbyPlayer.userId == currentUser?.userId && it.name == "You") ||
                            (lobbyPlayer.userId != currentUser?.userId && it.name == lobbyPlayer.username)
                        }
                        
                        Player(
                            name = if (lobbyPlayer.userId == currentUser?.userId) "You" else lobbyPlayer.username,
                            isReady = existingPlayer?.isReady ?: false,
                            selectedWords = existingPlayer?.selectedWords ?: emptyList(),
                            points = existingPlayer?.points ?: 0,
                            currentRoundPoints = existingPlayer?.currentRoundPoints ?: 0
                        )
                    }
                    
                    // Update spectator status for current user
                    val currentUserLobbyPlayer = updatedLobby.players.find { it.userId == currentUser?.userId }
                    if (currentUserLobbyPlayer != null) {
                        val newSpectatorStatus = currentUserLobbyPlayer.isSpectator
                        if (newSpectatorStatus != isSpectator) {
                            println("DEBUG GAME: Spectator status changed for current user: $isSpectator -> $newSpectatorStatus")
                            isSpectator = newSpectatorStatus
                        }
                    }
                    
                    // Only update if there are actual changes
                    if (updatedPlayers.size != players.size || 
                        !updatedPlayers.all { updatedPlayer ->
                            players.any { it.name == updatedPlayer.name }
                        }) {
                        println("DEBUG GAME: Player list changed from ${players.size} to ${updatedPlayers.size} players")
                        players.forEach { player -> println("DEBUG GAME: Old player: ${player.name}") }
                        updatedPlayers.forEach { player -> println("DEBUG GAME: New player: ${player.name}") }
                        players = updatedPlayers
                    }
                } else {
                    println("DEBUG GAME: Lobby no longer exists - should return to main menu")
                }
            }
        }
    }
    
    // Spectator overlay component
    @Composable
    fun SpectatorOverlay(phase: GamePhase) {
        if (isSpectator && (phase == GamePhase.PLAYING || phase == GamePhase.VOTING)) {
            println("DEBUG SPECTATOR: SHOWING OVERLAY for phase $phase, isSpectator=$isSpectator")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1F2937)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "👀",
                            fontSize = 48.sp
                        )
                        Text(
                            text = when (phase) {
                                GamePhase.PLAYING -> "Waiting for next round to begin"
                                GamePhase.VOTING -> "Watching voting in progress"
                                GamePhase.RESULTS -> "Waiting for next round to begin"
                                else -> "Waiting..."
                            },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "You'll be able to play starting next round!",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Button(
                            onClick = { onBackToMainMenu?.invoke() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Main Menu")
                        }
                    }
                }
            }
        }
    }

    when (gamePhase) {
        GamePhase.PLAYING -> {
            // Original word game UI
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(8.dp), // Reduced from 12dp to 8dp
                verticalArrangement = Arrangement.spacedBy(6.dp) // Reduced from 8dp to 6dp
            ) {
        
        // Player status and timer row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp), // Fixed height to prevent layout shifts
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player status
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Round indicator
                Text(
                    text = "Round $currentRound/$totalRounds",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                players.forEach { player ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (player.isReady) Color.Green.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (player.isReady) Color.Green else Color.Transparent) // Always have border to prevent layout shift
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            // Player name and ready status with fixed layout
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = player.name,
                                    color = if (player.isReady) Color.Green else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (player.isReady) FontWeight.Bold else FontWeight.Normal
                                )
                                // Fixed-width ready indicator to prevent layout shifts
                                Text(
                                    text = if (player.isReady) "✓" else " ",
                                    color = Color.Green,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(12.dp)
                                )
                            }
                            
                            // Show points on the right side (fixed width to prevent layout shifts)
                            Text(
                                text = if (player.points > 0) "${player.points} pts" else "",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
            
            // Timer
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (timeLeft <= 10) Color.Red.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color.Transparent) // Consistent border to prevent layout shifts
            ) {
                Text(
                    text = "${timeLeft}s",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        // Top arrangement bar
        ArrangementBar(
            arrangedWords = arrangedWords,
            draggedWordId = draggedWordId,
            isDraggingFromWordBank = isDraggingFromWordBank,
            wordBankDragPosition = wordBankDragPosition,
            onWordRemove = { word ->
                // Remove the word and update both game words and special words
                arrangedWords = arrangedWords.filter { it.id != word.id }
                gameWords = gameWords.map { 
                    if (it.id == word.id) it.copy(isPlaced = false) 
                    else it 
                }
                specialWords = specialWords.map { 
                    if (it.id == word.id) it.copy(isPlaced = false) 
                    else it 
                }
            },
            onWordsReordered = { newOrder ->
                arrangedWords = newOrder
                // Ensure all arranged words are marked as placed
                gameWords = gameWords.map { gameWord ->
                    if (newOrder.any { it.id == gameWord.id }) {
                        gameWord.copy(isPlaced = true)
                    } else {
                        gameWord
                    }
                }
                specialWords = specialWords.map { specialWord ->
                    if (newOrder.any { it.id == specialWord.id }) {
                        specialWord.copy(isPlaced = true)
                    } else {
                        specialWord
                    }
                }
            },
            onWordInsertAt = { word, position ->
                // Insert word at specific position
                val mutableList = arrangedWords.toMutableList()
                mutableList.add(position, word.copy(isPlaced = true))
                arrangedWords = mutableList
                gameWords = gameWords.map { 
                    if (it.id == word.id) it.copy(isPlaced = true) 
                    else it 
                }
                specialWords = specialWords.map { 
                    if (it.id == word.id) it.copy(isPlaced = true) 
                    else it 
                }
            },
            onDragStart = { wordId ->
                draggedWordId = wordId
            },
            onDragEnd = {
                draggedWordId = null
            },
            onActualPositionsUpdate = { positions ->
                actualWordPositions = positions
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    topBarGlobalX = coordinates.positionInRoot().x
                    topBarWidth = coordinates.size.width.toFloat()
                }
        )
        
        // Word Bank
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            verticalArrangement = Arrangement.spacedBy(4.dp), // Reduced from 8dp to 4dp
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(gameWords.filter { !it.isPlaced }) { word ->
                DraggableWordCard(
                    word = word,
                    isDragging = draggedWordId == word.id,
                    onDragStart = {
                        draggedWordId = word.id
                        isDraggingFromWordBank = true
                    },
                    onDragEndWithGlobalPosition = { dragOffset, finalGlobalPosition ->
                        // Update drag position for visual debugging
                        val relativeDropX = finalGlobalPosition.x - topBarGlobalX
                        wordBankDragPosition = relativeDropX
                        
                        // Then handle the actual insertion logic
                        if (draggedWordId == word.id) {
                            // Add to arrangement if not already there and dragged upward
                            if (!arrangedWords.any { it.id == word.id } && dragOffset.y < -50) {
                                
                                // Physics-based insertion: compare X positions to determine order
                                val dropX = finalGlobalPosition.x
                                val relativeDropX = dropX - topBarGlobalX
                                
                                // Use actual measured word positions for accurate insertion
                                var insertionIndex = 0
                                
                                if (arrangedWords.isEmpty()) {
                                    insertionIndex = 0
                                } else {
                                // Use ACTUAL word positions (same logic as top bar reordering)
                                var wordsToMyLeft = 0
                                
                                // Count how many words should be to my left in the final arrangement
                                for (i in arrangedWords.indices) {
                                    // Use ACTUAL position - if not available, skip (shouldn't happen)
                                    val wordPosition = actualWordPositions[i]
                                    if (wordPosition != null && relativeDropX > wordPosition) {
                                        // This word should be to my left in final arrangement
                                        wordsToMyLeft++
                                    }
                                }
                                
                                // My new index = number of words to my left
                                insertionIndex = wordsToMyLeft
                                insertionIndex = insertionIndex.coerceIn(0, arrangedWords.size)
                                
                                println("DEBUG WORD BANK INSERTION: relativeDropX=$relativeDropX, wordsToMyLeft=$wordsToMyLeft, insertionIndex=$insertionIndex")
                                println("ACTUAL POSITIONS: $actualWordPositions")
                            }
                                
                                // Insert word at calculated position
                                val mutableList = arrangedWords.toMutableList()
                                mutableList.add(insertionIndex, word.copy(isPlaced = true))
                                arrangedWords = mutableList
                                
                                gameWords = gameWords.map { 
                                    if (it.id == word.id) it.copy(isPlaced = true) 
                                    else it 
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggedWordId = null
                        isDraggingFromWordBank = false
                        wordBankDragPosition = 0f
                    },
                    onDrag = { offset ->
                        // Keep existing drag logic if needed
                    },
                    onDragWithGlobalPosition = { offset, currentGlobalPosition ->
                        // Update position during drag for visual debugging
                        if (draggedWordId == word.id) {
                            // Calculate current drag position relative to top bar
                            val relativeX = currentGlobalPosition.x - topBarGlobalX
                            wordBankDragPosition = relativeX
                            println("DEBUG WORD BANK DRAG: globalX=${currentGlobalPosition.x}, topBarX=$topBarGlobalX, relativeX=$relativeX")
                        }
                    },
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
        
        // Game Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onBackToMainMenu?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Main Menu")
                }

                Button(
                    onClick = {
                        // Mark player as ready
                        val playerSentence = arrangedWords.map { it.text }
                        
                        if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                            // Multiplayer mode - sync with Firestore
                            scope.launch {
                                println("DEBUG READY: Player clicking ready with sentence: $playerSentence")
                                val result = lobbyRepository.updatePlayerGameReady(
                                    gameLobby.gameId, 
                                    currentUser.userId, 
                                    true, 
                                    playerSentence
                                )
                                result.onSuccess {
                                    println("DEBUG READY: Successfully updated ready status in Firestore")
                                    // Update local state after successful sync
                                    players = players.map { 
                                        if (it.name == "You") it.copy(isReady = true, selectedWords = playerSentence) 
                                        else it 
                                    }
                                }.onFailure { error ->
                                    println("DEBUG READY: Failed to update game ready status: ${error.message}")
                                    // Still update local state to provide immediate feedback
                                    players = players.map { 
                                        if (it.name == "You") it.copy(isReady = true, selectedWords = playerSentence) 
                                        else it 
                                    }
                                }
                            }
                        } else {
                            // Practice mode - update local state only
                            players = players.map { 
                                if (it.name == "You") it.copy(isReady = true, selectedWords = playerSentence) 
                                else it 
                            }
                        }
                    },
                    enabled = !isSpectator && gamePhase == GamePhase.PLAYING && arrangedWords.isNotEmpty() && !players.first { it.name == "You" }.isReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981) // Modern green
                    )
                ) {
                    Text(
                        text = if (players.first { it.name == "You" }.isReady) "READY!" else "READY",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            // Special Words Section (right side)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                items(specialWords.filter { !it.isPlaced }) { word ->
                    DraggableWordCard(
                        word = word,
                        fillMaxSize = false, // Compact sizing for special words
                        isDragging = draggedWordId == word.id,
                        onDragStart = {
                            draggedWordId = word.id
                            isDraggingFromWordBank = true
                        },
                        onDragEndWithGlobalPosition = { dragOffset, finalGlobalPosition ->
                            val relativeX = finalGlobalPosition.x - topBarGlobalX
                            if (relativeX > 0 && relativeX < topBarWidth) {
                                onWordInsertAt(word, relativeX)
                            }
                            draggedWordId = null
                            isDraggingFromWordBank = false
                        },
                        onDragWithGlobalPosition = { dragOffset, globalPosition ->
                            wordBankDragPosition = globalPosition.x - topBarGlobalX
                            isDraggingFromWordBank = true
                        }
                    )
                }
            }
                }
            }
            
            // Spectator overlay for PLAYING phase
            SpectatorOverlay(GamePhase.PLAYING)

        }
        
        GamePhase.VOTING -> {
            // Voting screen
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voting header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vote for the best one",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (votingTimeLeft <= 5) Color.Red.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${votingTimeLeft}s",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                
                // Anonymous sentences - compact layout with randomized order (shuffled once)
                // For multiplayer, get sentences from Firestore game ready states
                var playersWithSentences by remember { mutableStateOf(players.filter { it.selectedWords.isNotEmpty() }) }
                
                // In multiplayer mode, refresh sentences from Firestore when entering voting
                if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                    LaunchedEffect(gamePhase) {
                        if (gamePhase == GamePhase.VOTING) {
                            val gameReadyResult = lobbyRepository.getGameReadyStates(gameLobby.gameId)
                            gameReadyResult.onSuccess { gameReadyStates ->
                                // Create players with sentences from Firestore
                                playersWithSentences = gameReadyStates.values.mapNotNull { gameState ->
                                    val selectedWords = gameState["selectedWords"] as? List<String>
                                    val username = gameState["username"] as? String
                                    if (!selectedWords.isNullOrEmpty() && username != null) {
                                        Player(
                                            name = username,
                                            isReady = true,
                                            selectedWords = selectedWords
                                        )
                                    } else null
                                }
                            }
                        }
                    }
                }
                
                val randomizedPlayersWithIndex = remember(gamePhase, currentRound, playersWithSentences) { 
                    playersWithSentences.shuffled().mapIndexed { index, player -> 
                        index to player 
                    }
                }
                
                randomizedPlayersWithIndex.forEach { (index, player) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSpectator && userVote == null) {
                                // Check if user is trying to vote for their own sentence
                                val isOwnSentence = if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                                    player.name == (currentUser.gameUsername ?: currentUser.displayName)
                                } else {
                                    player.name == "You"
                                }
                                
                                if (isOwnSentence) {
                                    showSelfVoteWarning = true
                                } else {
                                    userVote = index
                                    
                                    if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                                        // Multiplayer mode - submit vote to Firestore
                                        scope.launch {
                                            // Find the userId of the player being voted for
                                            val votedForUserId = gameLobby.players.find { it.username == player.name }?.userId
                                            if (votedForUserId != null) {
                                                val result = lobbyRepository.submitVote(gameLobby.gameId, currentUser.userId, votedForUserId)
                                                result.onFailure { error ->
                                                    println("Failed to submit vote: ${error.message}")
                                                }
                                            }
                                        }
                                    } else {
                                        // Practice mode - local vote handling (no CPU auto-voting in online mode)
                                        players = players.map { p ->
                                            if (p.name == player.name) {
                                                p.copy(currentRoundPoints = p.currentRoundPoints + 1)
                                            } else p
                                        }
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (userVote == index) Color.Green.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = smartJoinWords(player.selectedWords).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (userVote == index) {
                                Text(
                                    text = "✓",
                                    color = Color.Green,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                if (userVote != null) {
                    Text(
                        text = "Vote submitted! Waiting for other players...",
                        color = Color.Green,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Spectator overlay for VOTING phase
            SpectatorOverlay(GamePhase.VOTING)
        }
        
        GamePhase.RESULTS -> {
            // Debug logging for spectators stuck at results
            LaunchedEffect(resultsTimeLeft, isSpectator) {
                if (isSpectator && resultsTimeLeft <= 0) {
                    println("DEBUG SPECTATOR: STUCK AT RESULTS! resultsTimeLeft=$resultsTimeLeft, isSpectator=$isSpectator, currentRound=$currentRound")
                }
            }
            
            // Results screen - formatted like voting screen for 6 players
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Results header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Round Results",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${resultsTimeLeft}s",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                
                // Show all sentences sorted by current round points (highest first)
                var sortedPlayers by remember { mutableStateOf(players.filter { it.selectedWords.isNotEmpty() }.sortedByDescending { it.currentRoundPoints }) }
                
                // In multiplayer mode, get results from Firestore
                if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                    LaunchedEffect(gamePhase) {
                        if (gamePhase == GamePhase.RESULTS) {
                            // Get game ready states (sentences), voting results, and emoji awards
                            val gameReadyResult = lobbyRepository.getGameReadyStates(gameLobby.gameId)
                            val votingResult = lobbyRepository.getVotingResults(gameLobby.gameId)
                            val emojiResult = lobbyRepository.getSentenceEmojis(gameLobby.gameId)
                            
                            // Load emojis from Firebase
                            emojiResult.onSuccess { emojis ->
                                println("DEBUG EMOJI: Loaded ${emojis.size} emoji entries from Firebase")
                                sentenceEmojis = emojis
                            }.onFailure { error ->
                                println("DEBUG EMOJI: Failed to load emojis: ${error.message}")
                            }
                            
                            if (gameReadyResult.isSuccess && votingResult.isSuccess) {
                                val gameReadyStates = gameReadyResult.getOrNull() ?: emptyMap()
                                val votes = votingResult.getOrNull() ?: emptyMap()
                                
                                // Calculate points from votes
                                val voteCount = votes.values.groupingBy { it }.eachCount()
                                
                                // Update main players list with vote results
                                players = players.map { player ->
                                    // Find this player's userId by matching username  
                                    val matchingGameState = gameReadyStates.entries.find { (_, gameState) ->
                                        val username = gameState["username"] as? String
                                        when {
                                            // Direct match for other players
                                            username == player.name -> true
                                            // Current user: try multiple matching strategies
                                            player.name == "You" -> {
                                                username == currentUser?.gameUsername || 
                                                username == currentUser?.displayName ||
                                                username == (currentUser?.gameUsername ?: currentUser?.displayName)
                                            }
                                            else -> false
                                        }
                                    }
                                    
                                    if (matchingGameState != null) {
                                        val userId = matchingGameState.key
                                        val votesReceived = voteCount[userId] ?: 0
                                        println("DEBUG POINTS: Player ${player.name} (${userId}) received $votesReceived votes, current points: ${player.points}")
                                        player.copy(currentRoundPoints = votesReceived)
                                    } else {
                                        println("DEBUG POINTS: No matching game state found for player ${player.name}")
                                        if (player.name == "You") {
                                            println("DEBUG POINTS: Current user details - gameUsername: '${currentUser?.gameUsername}', displayName: '${currentUser?.displayName}'")
                                            println("DEBUG POINTS: Available usernames in Firebase: ${gameReadyStates.values.map { (it["username"] as? String) ?: "null" }}")
                                        }
                                        player
                                    }
                                }
                                
                                // Create players with sentences and calculated points for display
                                sortedPlayers = gameReadyStates.values.mapNotNull { gameState ->
                                    val selectedWords = gameState["selectedWords"] as? List<String>
                                    val username = gameState["username"] as? String
                                    val userId = gameReadyStates.entries.find { it.value == gameState }?.key
                                    
                                    if (!selectedWords.isNullOrEmpty() && username != null && userId != null) {
                                        val points = voteCount[userId] ?: 0
                                        Player(
                                            name = username,
                                            isReady = true,
                                            selectedWords = selectedWords,
                                            currentRoundPoints = points
                                        )
                                    } else null
                                }.sortedByDescending { it.currentRoundPoints }
                            }
                            
                            // Periodic emoji sync during results phase (every 2 seconds)
                            while (gamePhase == GamePhase.RESULTS) {
                                kotlinx.coroutines.delay(2000L)
                                val emojiSyncResult = lobbyRepository.getSentenceEmojis(gameLobby.gameId)
                                emojiSyncResult.onSuccess { updatedEmojis ->
                                    if (updatedEmojis != sentenceEmojis) {
                                        println("DEBUG EMOJI: Synced ${updatedEmojis.size} emoji entries from Firebase")
                                        sentenceEmojis = updatedEmojis
                                    }
                                }.onFailure { error ->
                                    println("DEBUG EMOJI: Failed to sync emojis: ${error.message}")
                                }
                            }
                        }
                    }
                }
                
                sortedPlayers.forEachIndexed { index, player ->
                    val sentenceEmojiList = sentenceEmojis[index] ?: emptyList()
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { 
                                    // Do nothing on tap in results phase
                                },
                                onLongClick = {
                                    selectedSentenceForEmoji = index
                                    showEmojiDialog = true
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Gray.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${smartJoinWords(player.selectedWords).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}${if (sentenceEmojiList.isNotEmpty()) " ${sentenceEmojiList.joinToString(" ")}" else ""}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Score and username on the right side  
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Previous points (gray/transparent white)
                                if (player.points > 0) {
                                    Text(
                                        text = "${player.points}",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                // Username
                                Text(
                                    text = player.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                // Current round points in green with +
                                if (player.currentRoundPoints > 0) {
                                    Text(
                                        text = "+${player.currentRoundPoints}",
                                        color = Color.Green,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
        }
        
        GamePhase.WINNER -> {
            // Winner screen with auto-return to lobby timer
            var returnToLobbyTimeLeft by remember { mutableStateOf(10) }
            
            // Auto-return to lobby timer (10 seconds)
            LaunchedEffect(gamePhase) {
                if (gamePhase == GamePhase.WINNER) {
                    while (returnToLobbyTimeLeft > 0) {
                        kotlinx.coroutines.delay(1000L)
                        returnToLobbyTimeLeft--
                    }
                    
                    // Return to lobby after timer expires
                    if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                        // Multiplayer mode - clear Firebase data and return to lobby
                        scope.launch {
                            println("DEBUG WINNER: Auto-returning to lobby, clearing Firebase data")
                            try {
                                // Clear all game data from Firebase
                                lobbyRepository.clearVotingData(gameLobby.gameId)
                                
                                // Also clear round signals to prevent interference
                                FirebaseFirestore.getInstance().collection("round_signals").document(gameLobby.gameId).delete().await()
                                
                                println("DEBUG WINNER: Successfully cleared Firebase data")
                            } catch (e: Exception) {
                                println("DEBUG WINNER: Failed to clear Firebase data: ${e.message}")
                            }
                            
                            // Return to main menu (which will show the lobby since we're still in the game)
                            onBackToMainMenu?.invoke()
                        }
                    } else {
                        // Practice mode - return to main menu
                        onBackToMainMenu?.invoke()
                    }
                }
            }
            
            // Winner screen with grand styling
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1A1A2E),
                                Color(0xFF16213E),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Trophy decorations
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏆",
                        fontSize = 48.sp
                    )
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "WINNER!",
                            color = Color(0xFFFFD700), // Gold color
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Winner's name in grand font
                        val winner = players.maxByOrNull { it.points }
                        Text(
                            text = winner?.name ?: "Unknown",
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "${winner?.points ?: 0} points",
                            color = Color(0xFF9CA3AF),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Text(
                        text = "🏆",
                        fontSize = 48.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Return to lobby countdown
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (gameLobby != null) {
                            "Returning to lobby in ${returnToLobbyTimeLeft}s"
                        } else {
                            "Returning to menu in ${returnToLobbyTimeLeft}s"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Return immediately button
                OutlinedButton(
                    onClick = {
                        if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                            // Multiplayer mode - clear Firebase data and return to lobby
                            scope.launch {
                                println("DEBUG WINNER: Manual return to lobby, clearing Firebase data")
                                try {
                                    // Clear all game data from Firebase
                                    lobbyRepository.clearVotingData(gameLobby.gameId)
                                    
                                    // Also clear round signals to prevent interference
                                    FirebaseFirestore.getInstance().collection("round_signals").document(gameLobby.gameId).delete().await()
                                    
                                    println("DEBUG WINNER: Successfully cleared Firebase data")
                                } catch (e: Exception) {
                                    println("DEBUG WINNER: Failed to clear Firebase data: ${e.message}")
                                }
                                
                                // Return to main menu (which will show the lobby since we're still in the game)
                                onBackToMainMenu?.invoke()
                            }
                        } else {
                            // Practice mode - return to main menu
                            onBackToMainMenu?.invoke()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (gameLobby != null) "Return to Lobby" else "Return to Menu",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    
    // Self-vote warning dialog
    if (showSelfVoteWarning) {
        SelfVoteWarningDialog(
            onDismiss = { showSelfVoteWarning = false }
        )
    }
    
    // Emoji selection dialog
    if (showEmojiDialog && selectedSentenceForEmoji != null) {
        EmojiSelectionDialog(
            onEmojiSelected = { emoji ->
                val sentenceIndex = selectedSentenceForEmoji!!
                
                if (gameLobby != null && currentUser != null && lobbyRepository != null) {
                    // Multiplayer mode - sync with Firebase
                    scope.launch {
                        println("DEBUG EMOJI: Awarding $emoji to sentence $sentenceIndex")
                        val result = lobbyRepository.awardEmoji(gameLobby.gameId, sentenceIndex, emoji, currentUser.userId)
                        result.onSuccess {
                            println("DEBUG EMOJI: Successfully awarded emoji")
                            // Update local state immediately for responsiveness
                            val currentEmojis = sentenceEmojis[sentenceIndex] ?: emptyList()
                            sentenceEmojis = sentenceEmojis + (sentenceIndex to currentEmojis + emoji)
                        }.onFailure { error ->
                            println("DEBUG EMOJI: Failed to award emoji: ${error.message}")
                            // Still update locally as fallback
                            val currentEmojis = sentenceEmojis[sentenceIndex] ?: emptyList()
                            sentenceEmojis = sentenceEmojis + (sentenceIndex to currentEmojis + emoji)
                        }
                    }
                } else {
                    // Practice mode - local only
                    val currentEmojis = sentenceEmojis[sentenceIndex] ?: emptyList()
                    sentenceEmojis = sentenceEmojis + (sentenceIndex to currentEmojis + emoji)
                }
                
                showEmojiDialog = false
                selectedSentenceForEmoji = null
            },
            onDismiss = {
                showEmojiDialog = false
                selectedSentenceForEmoji = null
            }
        )
    }
}
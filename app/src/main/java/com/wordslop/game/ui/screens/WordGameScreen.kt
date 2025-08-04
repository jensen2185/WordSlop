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
import com.wordslop.game.auth.UserInfo
import com.wordslop.game.repository.LobbyRepository
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val lobbyRepository = remember { LobbyRepository() }
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
    
    // Timer countdown effect for playing phase
    LaunchedEffect(gamePhase) {
        if (gamePhase == GamePhase.PLAYING) {
            while (timeLeft > 0 && gamePhase == GamePhase.PLAYING) {
                kotlinx.coroutines.delay(1000L)
                timeLeft--
                
                // Check if all players are ready
                if (players.all { it.isReady }) {
                    gamePhase = GamePhase.VOTING
                    break
                }
            }
            
            if (timeLeft <= 0) {
                gamePhase = GamePhase.VOTING
            }
        }
    }
    
    // Voting timer countdown effect
    LaunchedEffect(gamePhase) {
        if (gamePhase == GamePhase.VOTING) {
            while (votingTimeLeft > 0 && gamePhase == GamePhase.VOTING) {
                kotlinx.coroutines.delay(1000L)
                votingTimeLeft--
                
                // In multiplayer mode, check if all players have voted
                if (gameLobby != null && currentUser != null) {
                    val votingResult = lobbyRepository.getVotingResults(gameLobby.gameId)
                    votingResult.onSuccess { votes ->
                        // Check if all players have voted (number of votes equals number of players)
                        if (votes.size >= gameLobby.players.size) {
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
    
    // Results timer countdown effect
    LaunchedEffect(gamePhase) {
        if (gamePhase == GamePhase.RESULTS) {
            while (resultsTimeLeft > 0 && gamePhase == GamePhase.RESULTS) {
                kotlinx.coroutines.delay(1000L)
                resultsTimeLeft--
            }
            
            // Auto-proceed after 5 seconds
            if (resultsTimeLeft <= 0) {
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
                    
                    // Clear voting data for multiplayer mode (fire and forget)
                    if (gameLobby != null && currentUser != null) {
                        scope.launch {
                            lobbyRepository.clearVotingData(gameLobby.gameId)
                        }
                    }
                } else {
                    // Game finished - transfer final round points and show winner
                    players = players.map { 
                        it.copy(
                            points = it.points + it.currentRoundPoints, // Transfer final round points to total
                            currentRoundPoints = 0 // Reset round points
                        )
                    }
                    gamePhase = GamePhase.WINNER
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
    
    // Multiplayer ready status sync (only in multiplayer mode)
    if (gameLobby != null && currentUser != null) {
        val updatedLobby by lobbyRepository.getLobbyFlow(gameLobby.gameId).collectAsState(initial = gameLobby)
        
        // Periodic sync for ready states during playing phase (every 2 seconds)
        LaunchedEffect(gamePhase) {
            if (gamePhase == GamePhase.PLAYING) {
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
                                    selectedWords = newWords
                                )
                            } else {
                                localPlayer
                            }
                        }
                        
                        // Only update if there are actual changes
                        if (updatedPlayers != players) {
                            players = updatedPlayers
                        }
                        
                        // Check if all players are ready for voting
                        val allPlayersReady = gameReadyStates.values.all { 
                            it["isReady"] as? Boolean ?: false 
                        }
                        if (allPlayersReady && gameReadyStates.size >= 2) {
                            println("DEBUG GAME: All players ready, proceeding to voting")
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
            modifier = Modifier.fillMaxWidth(),
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
                        border = if (player.isReady) BorderStroke(1.dp, Color.Green) else null
                    ) {
                        Text(
                            text = if (player.isReady) "${player.name} ✓" else player.name,
                            color = if (player.isReady) Color.Green else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (player.isReady) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // Timer
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (timeLeft <= 10) Color.Red.copy(alpha = 0.3f) else Color.Blue.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(4.dp)
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
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Main Menu")
                }

                Button(
                    onClick = {
                        // Mark player as ready
                        val playerSentence = arrangedWords.map { it.text }
                        
                        if (gameLobby != null && currentUser != null) {
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
                    enabled = gamePhase == GamePhase.PLAYING && arrangedWords.isNotEmpty() && !players.first { it.name == "You" }.isReady,
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
                            containerColor = if (votingTimeLeft <= 5) Color.Red.copy(alpha = 0.3f) else Color(0xFFFF9800).copy(alpha = 0.3f)
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
                if (gameLobby != null && currentUser != null) {
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
                            .clickable(enabled = userVote == null) {
                                // Check if user is trying to vote for their own sentence
                                val isOwnSentence = if (gameLobby != null && currentUser != null) {
                                    player.name == (currentUser.gameUsername ?: currentUser.displayName)
                                } else {
                                    player.name == "You"
                                }
                                
                                if (isOwnSentence) {
                                    showSelfVoteWarning = true
                                } else {
                                    userVote = index
                                    
                                    if (gameLobby != null && currentUser != null) {
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
        }
        
        GamePhase.RESULTS -> {
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
                            containerColor = Color(0xFFFF9800).copy(alpha = 0.3f)
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
                if (gameLobby != null && currentUser != null) {
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
                                
                                // Create players with sentences and calculated points
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
                            
                            // Username and points on the right side
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = player.name,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                // Previous points in yellow (if any)
                                if (player.points > 0) {
                                    Text(
                                        text = "${player.points}",
                                        color = Color.Yellow,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
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
                
                // Play again button
                Button(
                    onClick = {
                        // Reset entire game
                        gameWords = wordRepository.getRandomWords()
                        specialWords = wordRepository.getSpecialWords()
                        arrangedWords = emptyList()
                        timeLeft = 60
                        votingTimeLeft = 20
                        resultsTimeLeft = 5
                        currentRound = 1
                        gamePhase = GamePhase.PLAYING
                        userVote = null
                        sentenceEmojis = emptyMap() // Clear emoji tags for new game
                        players = players.map { it.copy(isReady = false, selectedWords = emptyList(), points = 0, currentRoundPoints = 0) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Play Again",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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
                
                if (gameLobby != null && currentUser != null) {
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
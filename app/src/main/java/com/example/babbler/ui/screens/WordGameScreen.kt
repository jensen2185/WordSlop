package com.example.babbler.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.babbler.model.Word
import com.example.babbler.repository.WordRepository
import com.example.babbler.ui.components.WordCard
import com.example.babbler.ui.components.DraggableWordCard
import com.example.babbler.ui.components.ArrangementBar
import kotlin.math.roundToInt

data class Player(
    val name: String,
    val isReady: Boolean = false,
    val selectedWords: List<String> = emptyList(),
    val points: Int = 0
)

enum class GamePhase {
    PLAYING, VOTING, RESULTS, WINNER
}

@Composable
fun WordGameScreen(
    modifier: Modifier = Modifier
) {
    val wordRepository = remember { WordRepository() }
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
    var totalRounds by remember { mutableStateOf(2) }
    var players by remember { 
        mutableStateOf(listOf(
            Player("You", false, emptyList()),
            Player("CPU1", false, emptyList()),
            Player("CPU2", false, emptyList()),
            Player("CPU3", false, emptyList()),
            Player("CPU4", false, emptyList()),
            Player("CPU5", false, emptyList())
        )) 
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
            while (votingTimeLeft > 0 && gamePhase == GamePhase.VOTING && userVote == null) {
                kotlinx.coroutines.delay(1000L)
                votingTimeLeft--
            }
            
            // If user voted or time ran out, move to results
            if (userVote != null || votingTimeLeft <= 0) {
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
                    // Go to next round
                    currentRound++
                    gameWords = wordRepository.getRandomWords()
                    specialWords = wordRepository.getSpecialWords()
                    arrangedWords = emptyList()
                    timeLeft = 60
                    votingTimeLeft = 20
                    resultsTimeLeft = 5
                    gamePhase = GamePhase.PLAYING
                    userVote = null
                    players = players.map { it.copy(isReady = false, selectedWords = emptyList()) }
                } else {
                    // Game finished - show winner
                    gamePhase = GamePhase.WINNER
                }
            }
        }
    }
    
    // CPU simulation effect
    LaunchedEffect(gamePhase) {
        if (gamePhase == GamePhase.PLAYING) {
            // Simulate CPU players selecting words and hitting ready immediately
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
                            containerColor = if (player.isReady) Color.Green.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = player.name,
                            color = Color.White,
                            fontSize = 12.sp,
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
                        // Reset game
                        gameWords = wordRepository.getRandomWords()
                        specialWords = wordRepository.getSpecialWords()
                        arrangedWords = emptyList()
                        timeLeft = 60
                        votingTimeLeft = 20
                        resultsTimeLeft = 5
                        currentRound = 1
                        gamePhase = GamePhase.PLAYING
                        userVote = null
                        players = players.map { it.copy(isReady = false, selectedWords = emptyList(), points = 0) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("New Game")
                }

                Button(
                    onClick = {
                        // Mark player as ready
                        val playerSentence = arrangedWords.map { it.text }
                        players = players.map { 
                            if (it.name == "You") it.copy(isReady = true, selectedWords = playerSentence) 
                            else it 
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
                        text = "Vote for the Best Sentence",
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
                
                // Anonymous sentences - compact layout
                val sentencesWithIndex = players.filter { it.selectedWords.isNotEmpty() }.mapIndexed { index, player -> 
                    index to player 
                }
                
                sentencesWithIndex.forEach { (index, player) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = userVote == null) {
                                userVote = index
                                // Award point to voted player and CPUs vote for user
                                players = players.map { p ->
                                    when {
                                        p.name == player.name -> p.copy(points = p.points + 1) // User's vote
                                        p.name == "You" -> p.copy(points = p.points + 3) // CPUs vote for user
                                        else -> p
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
                                text = player.selectedWords.joinToString(" ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
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
                        text = "CPUs voted for your sentence! Moving to results...",
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
                
                // Show all sentences with authors and points - compact layout
                players.filter { it.selectedWords.isNotEmpty() }.forEach { player ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                                text = "${player.name}: ${player.selectedWords.joinToString(" ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (player.points > 0) {
                                Text(
                                    text = "+${player.points}",
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
                        players = players.map { it.copy(isReady = false, selectedWords = emptyList(), points = 0) }
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
}
package com.example.babbler.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.babbler.model.Word
import com.example.babbler.repository.WordRepository
import com.example.babbler.ui.components.WordCard
import com.example.babbler.ui.components.DraggableWordCard
import com.example.babbler.ui.components.ArrangementBar
import kotlin.math.roundToInt

@Composable
fun WordGameScreen(
    modifier: Modifier = Modifier
) {
    val wordRepository = remember { WordRepository() }
    var gameWords by remember { mutableStateOf(wordRepository.getRandomWords()) }
    // Simple list of words in order
    var arrangedWords by remember { mutableStateOf<List<Word>>(emptyList()) }
    var draggedWordId by remember { mutableStateOf<Int?>(null) }
    var topBarGlobalX by remember { mutableStateOf(0f) }
    var topBarWidth by remember { mutableStateOf(0f) }
    
    val configuration = LocalConfiguration.current
    
    // Determine grid columns based on screen orientation  
    val gridColumns = if (configuration.screenWidthDp > configuration.screenHeightDp) {
        // Landscape mode - more columns for horizontal optimization
        6
    } else {
        // Portrait mode - fewer columns
        4
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top arrangement bar
        ArrangementBar(
            arrangedWords = arrangedWords,
            draggedWordId = draggedWordId,
            onWordRemove = { word ->
                // Simply remove the word and update game words
                arrangedWords = arrangedWords.filter { it.id != word.id }
                gameWords = gameWords.map { 
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
            },
            onDragStart = { wordId ->
                draggedWordId = wordId
            },
            onDragEnd = {
                draggedWordId = null
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    },
                    onDragEndWithGlobalPosition = { dragOffset, finalGlobalPosition ->
                        if (draggedWordId == word.id) {
                            // Add to arrangement if not already there and dragged upward
                            if (!arrangedWords.any { it.id == word.id } && dragOffset.y < -50) {
                                
                                // Physics-based insertion: compare X positions to determine order
                                val dropX = finalGlobalPosition.x
                                val relativeDropX = dropX - topBarGlobalX
                                
                                // SIMPLE APPROACH: Just divide position by reasonable spacing
                                var insertionIndex = 0
                                
                                if (arrangedWords.isEmpty()) {
                                    insertionIndex = 0
                                } else {
                                    // Dead simple: every ~70px is roughly one word position
                                    val approximateWordSpacing = 70f
                                    insertionIndex = (relativeDropX / approximateWordSpacing).roundToInt()
                                    insertionIndex = insertionIndex.coerceIn(0, arrangedWords.size)
                                    
                                    println("DEBUG SIMPLE: relativeDropX=$relativeDropX, spacing=$approximateWordSpacing, calculatedIndex=$insertionIndex")
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
                    },
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
        
        // Game Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    // Reset game
                    gameWords = wordRepository.getRandomWords()
                    arrangedWords = emptyList()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("New Game")
            }
            
            Button(
                onClick = {
                    // Clear arrangement
                    arrangedWords = emptyList()
                    gameWords = gameWords.map { it.copy(isPlaced = false) }
                }
            ) {
                Text("Clear")
            }
        }
    }
}
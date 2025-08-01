package com.example.babbler.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.babbler.model.Word
import com.example.babbler.repository.WordRepository
import com.example.babbler.ui.components.WordCard
import com.example.babbler.ui.components.DraggableWordCard
import com.example.babbler.ui.components.ArrangementBar

@Composable
fun WordGameScreen(
    modifier: Modifier = Modifier
) {
    val wordRepository = remember { WordRepository() }
    var gameWords by remember { mutableStateOf(wordRepository.getRandomWords()) }
    var arrangedWords by remember { mutableStateOf<List<Word>>(emptyList()) }
    var draggedWordId by remember { mutableStateOf<Int?>(null) }
    
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top arrangement bar
        ArrangementBar(
            arrangedWords = arrangedWords,
            draggedWordId = draggedWordId,
            onWordRemove = { word ->
                // Remove word from arrangement and make it available again
                val updatedArrangedWords = arrangedWords.filter { it.id != word.id }
                arrangedWords = updatedArrangedWords
                
                // Update game words to reflect the removal
                gameWords = gameWords.map { 
                    if (it.id == word.id) it.copy(isPlaced = false) 
                    else it 
                }
            },
            onWordsReordered = { newOrder ->
                arrangedWords = newOrder
            },
            onDragStart = { wordId ->
                draggedWordId = wordId
            },
            onDragEnd = {
                draggedWordId = null
            },
            modifier = Modifier.fillMaxWidth()
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
            items(gameWords.filter { !it.isPlaced && !arrangedWords.any { arranged -> arranged.id == it.id } }) { word ->
                DraggableWordCard(
                    word = word,
                    isDragging = draggedWordId == word.id,
                    onDragStart = {
                        draggedWordId = word.id
                    },
                    onDragEnd = {
                        if (draggedWordId == word.id) {
                            // Only add to arrangement if not already there
                            if (!arrangedWords.any { it.id == word.id }) {
                                arrangedWords = arrangedWords + word.copy(isPlaced = true)
                                gameWords = gameWords.map { 
                                    if (it.id == word.id) it.copy(isPlaced = true) 
                                    else it 
                                }
                            }
                        }
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
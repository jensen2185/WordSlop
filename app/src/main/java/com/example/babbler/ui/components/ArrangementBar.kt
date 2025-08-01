package com.example.babbler.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.babbler.model.Word
import kotlin.math.roundToInt

@Composable
fun ArrangementBar(
    arrangedWords: List<Word>,
    modifier: Modifier = Modifier,
    onWordRemove: (Word) -> Unit = {},
    onWordsReordered: (List<Word>) -> Unit = {},
    draggedWordId: Int? = null,
    onDragStart: (Int) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        if (arrangedWords.isEmpty()) {
            Text(
                text = "Drag words here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterStart)
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(arrangedWords) { index, word ->
                    DraggableArrangedWord(
                        word = word,
                        index = index,
                        arrangedWords = arrangedWords,
                        onWordRemove = onWordRemove,
                        onWordsReordered = onWordsReordered,
                        onDragStart = onDragStart,
                        onDragEnd = onDragEnd,
                        modifier = Modifier.height(44.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableArrangedWord(
    word: Word,
    index: Int,
    arrangedWords: List<Word>,
    onWordRemove: (Word) -> Unit,
    onWordsReordered: (List<Word>) -> Unit,
    onDragStart: (Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        ),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
        modifier = modifier
            .offset { 
                IntOffset(
                    offset.x.roundToInt(), 
                    offset.y.roundToInt()
                ) 
            }
            .zIndex(if (isDragging) 100f else 0f)
            .pointerInput(word.id) {
                detectDragGestures(
                    onDragStart = { 
                        isDragging = true
                        onDragStart(word.id)
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd()
                        
                        // Check if dragged down significantly (remove from arrangement)
                        if (offset.y > 80) {
                            onWordRemove(word)
                            return@detectDragGestures
                        } else if (kotlin.math.abs(offset.x) > 50) {
                            // Horizontal drag - reorder within arrangement
                            val cardWidth = 80 // Approximate width including spacing
                            val positionChange = (offset.x / cardWidth).roundToInt()
                            val newIndex = (index + positionChange).coerceIn(0, arrangedWords.size - 1)
                            
                            if (newIndex != index) {
                                val mutableList = arrangedWords.toMutableList()
                                val draggedWord = mutableList.removeAt(index)
                                mutableList.add(newIndex, draggedWord)
                                onWordsReordered(mutableList)
                            }
                        }
                        
                        offset = Offset.Zero
                    }
                ) { change, dragAmount ->
                    offset += dragAmount
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .wrapContentSize()
        ) {
            Text(
                text = word.text,
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
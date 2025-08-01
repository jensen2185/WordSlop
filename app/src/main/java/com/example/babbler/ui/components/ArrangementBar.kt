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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.babbler.model.Word
import kotlin.math.roundToInt
import kotlin.math.abs

@Composable
fun ArrangementBar(
    arrangedWords: List<Word>,
    modifier: Modifier = Modifier,
    onWordRemove: (Word) -> Unit = {},
    onWordsReordered: (List<Word>) -> Unit = {},
    onWordInsertAt: (Word, Int) -> Unit = { _, _ -> },
    draggedWordId: Int? = null,
    onDragStart: (Int) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    var barGlobalX by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color.Black)
            .padding(8.dp)

    ) {
        // Words container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .align(Alignment.TopCenter)
                .onGloballyPositioned { coordinates ->
                    barGlobalX = coordinates.positionInRoot().x
                }
        ) {
            // Line below words
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.3f))
                    .align(Alignment.BottomCenter)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top
            ) {
                itemsIndexed(
                    items = arrangedWords,
                    key = { index, word -> "${word.id}-$index" }
                ) { index, word ->
                    // Each word in the list should be visible
                                               DraggableArrangedWord(
                               word = word,
                               index = index,
                               arrangedWords = arrangedWords,
                               barGlobalX = barGlobalX,
                               onWordRemove = onWordRemove,
                               onWordsReordered = onWordsReordered,
                               onDragStart = onDragStart,
                               onDragEnd = onDragEnd,
                               modifier = Modifier
                                   .height(44.dp)
                                   .zIndex(1f)  // Ensure words are always visible
                           )
                }
            }
        }
    }
}

@Composable
fun DraggableArrangedWord(
    word: Word,
    index: Int,
    arrangedWords: List<Word>,
    barGlobalX: Float,
    onWordRemove: (Word) -> Unit,
    onWordsReordered: (List<Word>) -> Unit,
    onDragStart: (Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var initialGlobalPosition by remember { mutableStateOf(Offset.Zero) }
    
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
            .onGloballyPositioned { coordinates ->
                if (!isDragging) {
                    initialGlobalPosition = coordinates.positionInRoot()
                }
            }
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
                        } else {
                            // Physics-based reordering: use actual drop position
                            val finalGlobalPosition = initialGlobalPosition + offset
                            val dropX = finalGlobalPosition.x
                            val relativeDropX = dropX - barGlobalX
                            
                            println("DEBUG TOP BAR: dropX=$dropX, barGlobalX=$barGlobalX, relativeDropX=$relativeDropX, currentIndex=$index")
                            
                            // DEAD SIMPLE: Just divide position by spacing
                            val approximateWordSpacing = 70f
                            var newIndex = (relativeDropX / approximateWordSpacing).roundToInt()
                            newIndex = newIndex.coerceIn(0, arrangedWords.size - 1)
                            
                            // Adjust for removing current word first
                            if (newIndex > index) {
                                newIndex -= 1
                            }
                            
                            println("DEBUG TOP BAR SIMPLE: relativeDropX=$relativeDropX, targetIndex=$newIndex, currentIndex=$index")
                            
                            // Only reorder if position actually changed and movement is significant
                            if (newIndex != index && kotlin.math.abs(offset.x) > 30f) {
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
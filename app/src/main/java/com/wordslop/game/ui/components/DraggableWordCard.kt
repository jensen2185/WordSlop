package com.wordslop.game.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
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
import com.wordslop.game.model.Word
import kotlin.math.roundToInt

@Composable
fun DraggableWordCard(
    word: Word,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    fillMaxSize: Boolean = true, // Fill grid cell (main bank) vs wrap content (special words)
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragEndWithPosition: (Offset) -> Unit = { _ -> },
    onDragEndWithGlobalPosition: (Offset, Offset) -> Unit = { _, _ -> },
    onDrag: (Offset) -> Unit = {},
    onDragWithGlobalPosition: (Offset, Offset) -> Unit = { _, _ -> }
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var initialGlobalPosition by remember { mutableStateOf(Offset.Zero) }
    
    Card(
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
                        onDragStart()
                    },
                    onDragEnd = {
                        val finalGlobalPosition = initialGlobalPosition + offset
                        onDragEndWithPosition(offset)
                        onDragEndWithGlobalPosition(offset, finalGlobalPosition)
                        onDragEnd()
                        offset = Offset.Zero
                    }
                                       ) { change, dragAmount ->
                           offset += dragAmount
                           onDrag(offset)
                           
                           // Also report current global position during drag
                           val currentGlobalPosition = initialGlobalPosition + offset
                           onDragWithGlobalPosition(offset, currentGlobalPosition)
                       }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) Color(0xFFE5E7EB) else Color(0xFFF8F9FA) // Slightly darker gray when dragging, light grayish white otherwise
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 12.dp else 6.dp
        ),
        border = BorderStroke(
            width = if (isDragging) 3.dp else 2.dp,
            color = if (isDragging) Color(0xFFA855F7) else Color(0xFF818CF8).copy(alpha = 0.4f)
        )
    ) {
                       Box(
                   contentAlignment = Alignment.Center,
                   modifier = Modifier
                       .padding(
                           start = if (word.text == "'s" || word.text == "er" || word.text == "s") 1.dp else 12.dp,
                           end = 12.dp,
                           top = 4.dp, // Reduced from 8dp to 4dp for shorter cards
                           bottom = 4.dp
                       )
                       .then(
                           if (fillMaxSize) Modifier.fillMaxSize() // Grid cells - no gaps
                           else Modifier.wrapContentWidth().wrapContentHeight() // Special words - compact
                       )
               ) {
                   Text(
                       text = word.text,
                       color = Color(0xFF1F2937), // Dark gray text on light background
                       fontSize = 16.sp,
                       fontWeight = FontWeight.SemiBold,
                       maxLines = 1,
                       overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                   )
        }
    }
}
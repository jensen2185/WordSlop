package com.example.babbler.ui.components

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
import com.example.babbler.model.Word
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
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        ),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
                       Box(
                   contentAlignment = Alignment.Center,
                   modifier = Modifier
                       .padding(
                           start = if (word.text == "'s") 1.dp else 8.dp, // Minimal left padding for 's
                           end = 8.dp,
                           top = 6.dp,
                           bottom = 6.dp
                       )
                       .then(
                           if (fillMaxSize) Modifier.fillMaxSize() // Grid cells - no gaps
                           else Modifier.wrapContentWidth().wrapContentHeight() // Special words - compact
                       )
               ) {
                                   Text(
                           text = word.text,
                           color = Color.Black,
                           fontSize = 16.sp,
                           fontWeight = FontWeight.Normal,
                           maxLines = 1,
                           overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                       )
        }
    }
}
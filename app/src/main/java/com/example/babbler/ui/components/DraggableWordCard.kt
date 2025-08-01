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
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragEndWithPosition: (Offset) -> Unit = { _ -> },
    onDragEndWithGlobalPosition: (Offset, Offset) -> Unit = { _, _ -> },
    onDrag: (Offset) -> Unit = {}
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
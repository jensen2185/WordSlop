package com.example.wordslop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wordslop.model.Word

@Composable
fun WordCard(
    word: Word,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F9FA) // Light grayish white
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        ),
        border = BorderStroke(2.dp, Color(0xFF818CF8).copy(alpha = 0.4f))
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
                .fillMaxSize()
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
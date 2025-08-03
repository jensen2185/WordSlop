package com.wordslop.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
// Removed problematic icon import
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordslop.game.model.GameSettings
import com.wordslop.game.model.GameMode
import com.wordslop.game.auth.UserInfo

@Composable
fun CreateGameScreen(
    userInfo: UserInfo,
    onGameCreated: (GameSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPublic by remember { mutableStateOf(true) }
    var passcode by remember { mutableStateOf("") }
    var numberOfRounds by remember { mutableStateOf(3) }
    val maxPlayers = 6 // Fixed maximum players for voting screen compatibility
    var showPasscodeError by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Text(
                text = "Create New Game",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Game Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1F2937)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Game Type and Rounds in one row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Game Type Selection (Left side)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Type",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Public Game Button - Compact
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isPublic = true },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPublic) Color(0xFF10B981) else Color(0xFF374151)
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "🌍",
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Public",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            
                            // Private Game Button - Compact
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isPublic = false },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!isPublic) Color(0xFFFF9800) else Color(0xFF374151)
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Private",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Private",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    // Number of Rounds (Right side)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Rounds",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(3, 7, 10).forEach { rounds ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { numberOfRounds = rounds },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (numberOfRounds == rounds) Color(0xFF10B981) else Color(0xFF374151)
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "$rounds",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                

                // Passcode for Private Games
                if (!isPublic) {
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = { 
                            passcode = it
                            showPasscodeError = false
                        },
                        label = { Text("4-digit passcode", color = Color.Gray) },
                        placeholder = { Text("1234", color = Color.Gray) },
                        isError = showPasscodeError,
                        supportingText = {
                            if (showPasscodeError) {
                                Text("Passcode must be 4 digits", color = Color.Red)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Create Game Button
        Button(
            onClick = {
                // Validate private game passcode
                if (!isPublic && (passcode.length != 4 || !passcode.all { it.isDigit() })) {
                    showPasscodeError = true
                    return@Button
                }
                
                val gameSettings = GameSettings(
                    isPublic = isPublic,
                    passcode = if (isPublic) null else passcode,
                    numberOfRounds = numberOfRounds,
                    maxPlayers = maxPlayers,
                    gameMode = GameMode.ONLINE // All created games are online mode
                )
                onGameCreated(gameSettings)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF10B981)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Create Game",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
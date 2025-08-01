package com.example.babbler.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainMenuScreen(
    modifier: Modifier = Modifier,
    onPlayOnline: () -> Unit = {},
    onPracticeMode: () -> Unit = {}
) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        
        // Left side - Title
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BABBLER",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Word Arrangement Game",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
        
        // Right side - Login and buttons
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Login Section
            if (!isLoggedIn) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Account",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Text(
                        text = "Create Account or Sign In",
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Button(
                        onClick = {
                            // Simulate Google login
                            isLoggedIn = true
                            username = "Player_${(1000..9999).random()}"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Sign in with Google",
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    OutlinedButton(
                        onClick = {
                            // Simulate username creation
                            isLoggedIn = true
                            username = "Guest_${(100..999).random()}"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Create Username")
                    }
                }
            }
            } else {
                // Welcome back section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Welcome back!",
                        fontSize = 18.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = username,
                        fontSize = 24.sp,
                        color = Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Game Mode Buttons
            if (isLoggedIn) {
                Button(
                    onClick = onPlayOnline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "PLAY ONLINE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onPracticeMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "PRACTICE MODE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Version info
            Text(
                text = "Version 1.0 - Beta",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}
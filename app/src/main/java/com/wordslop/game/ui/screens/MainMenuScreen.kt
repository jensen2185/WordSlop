package com.wordslop.game.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordslop.game.auth.AuthResult
import com.wordslop.game.auth.FirebaseAuthManager
import com.wordslop.game.auth.UserInfo
import com.wordslop.game.auth.createGuestUser
import com.wordslop.game.ui.components.UsernameSelectionDialog
import com.wordslop.game.ui.components.GuestLoginDialog
import com.wordslop.game.repository.LobbyRepository
import kotlinx.coroutines.launch

@Composable
fun MainMenuScreen(
    modifier: Modifier = Modifier,
    onJoinGame: (UserInfo) -> Unit = {},
    onCreateGame: (UserInfo) -> Unit = {},
    onPracticeMode: (UserInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val authManager = remember { FirebaseAuthManager(context) }
    val lobbyRepository = remember { LobbyRepository() }
    val scope = rememberCoroutineScope()
    
    var userInfo by remember { mutableStateOf(authManager.getCurrentUserInfo()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showGuestDialog by remember { mutableStateOf(false) }
    var pendingGoogleUserInfo by remember { mutableStateOf<AuthResult.Success?>(null) }
    
    val isLoggedIn = userInfo != null
    
    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            isLoading = true
            errorMessage = null
            
            val authResult = authManager.handleSignInResult(result.data)
            when (authResult) {
                is AuthResult.Success -> {
                    // Store the Google auth result and show username selection
                    pendingGoogleUserInfo = authResult
                    showUsernameDialog = true
                }
                is AuthResult.Error -> {
                    errorMessage = authResult.message
                }
            }
            isLoading = false
        }
    }

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
                text = "WORD SLOP",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Get your slop fix",
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
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                authManager.startGoogleSignIn(signInLauncher)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black
                            )
                        } else {
                            Text(
                                text = "Sign in with Google",
                                color = Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // Show error message if any
                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = Color.Red,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = Color.Gray.copy(alpha = 0.3f)
                        )
                        Text(
                            text = " OR ",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Divider(
                            modifier = Modifier.weight(1f),
                            color = Color.Gray.copy(alpha = 0.3f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Play as Guest button
                    OutlinedButton(
                        onClick = {
                            showGuestDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF9800)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Play as Guest",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            } else {
                // Welcome back section - compact horizontal layout
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side - User info
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Welcome back!",
                                fontSize = 14.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Text(
                                text = userInfo?.gameUsername ?: userInfo?.displayName ?: "Unknown User",
                                fontSize = 18.sp,
                                color = if (userInfo?.isGuest == true) Color(0xFFFF9800) else Color.Green,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (userInfo?.isGuest == true) {
                                Text(
                                    text = "Guest Player",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                userInfo?.email?.let { email ->
                                    Text(
                                        text = email,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                        
                        // Right side - Sign out button
                        OutlinedButton(
                            onClick = {
                                if (userInfo?.isGuest != true) {
                                    authManager.signOut()
                                }
                                userInfo = null
                                errorMessage = null
                                pendingGoogleUserInfo = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Sign Out",
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Game Mode Buttons
            if (isLoggedIn) {
                Button(
                    onClick = { userInfo?.let { onJoinGame(it) } },
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
                        text = "JOIN GAME",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { userInfo?.let { onCreateGame(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981) // Green color for create game
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "CREATE GAME",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { userInfo?.let { onPracticeMode(it) } },
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
            
            // Debug buttons (temporary)
            if (isLoggedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                println("QUERY: Querying all Firebase lobbies")
                                lobbyRepository.debugAllLobbies()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("QUERY FB", fontSize = 10.sp)
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                println("NUCLEAR: Deleting ALL lobbies")
                                val result = lobbyRepository.deleteAllLobbies()
                                if (result.isSuccess) {
                                    println("NUCLEAR: Successfully deleted all lobbies")
                                } else {
                                    println("NUCLEAR: Failed to delete lobbies: ${result.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("DELETE ALL", fontSize = 10.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Version info
            Text(
                text = "Version 1.0 - Beta",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
    
    // Username Selection Dialog
    if (showUsernameDialog) {
        pendingGoogleUserInfo?.let { googleAuth ->
            UsernameSelectionDialog(
                defaultUsername = googleAuth.displayName.split(" ").firstOrNull() ?: "Player",
                onUsernameConfirmed = { customUsername ->
                    userInfo = com.wordslop.game.auth.UserInfo(
                        userId = googleAuth.userId,
                        displayName = googleAuth.displayName,
                        email = googleAuth.email,
                        gameUsername = customUsername,
                        isGuest = false
                    )
                    showUsernameDialog = false
                    pendingGoogleUserInfo = null
                },
                onDismiss = {
                    showUsernameDialog = false
                    pendingGoogleUserInfo = null
                    authManager.signOut() // Sign out if they cancel username selection
                }
            )
        }
    }
    
    // Guest Login Dialog
    if (showGuestDialog) {
        GuestLoginDialog(
            onGuestLogin = { guestUsername ->
                userInfo = createGuestUser(guestUsername)
                showGuestDialog = false
                errorMessage = null
            },
            onDismiss = {
                showGuestDialog = false
            }
        )
    }
}
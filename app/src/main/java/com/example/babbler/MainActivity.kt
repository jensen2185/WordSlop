package com.example.babbler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.babbler.ui.screens.MainMenuScreen
import com.example.babbler.ui.screens.WordGameScreen

sealed class Screen {
    object MainMenu : Screen()
    object JoinGame : Screen()
    object CreateGame : Screen()
    object GameLobby : Screen()
    object WordGame : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure status bar styling
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false // Dark status bar content
        
        setContent {
            MaterialTheme {
                BabblerApp()
            }
        }
    }
}

@Composable
fun BabblerApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.MainMenu) }
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) { innerPadding ->
        when (currentScreen) {
            Screen.MainMenu -> {
                MainMenuScreen(
                    modifier = Modifier.padding(innerPadding),
                    onJoinGame = {
                        currentScreen = Screen.JoinGame
                    },
                    onCreateGame = {
                        currentScreen = Screen.CreateGame
                    },
                    onPracticeMode = {
                        currentScreen = Screen.WordGame
                    }
                )
            }
            
            Screen.JoinGame -> {
                // TODO: Implement JoinGameScreen
                PlaceholderScreen(
                    title = "Join Game",
                    subtitle = "Browse available games to join",
                    onBack = { currentScreen = Screen.MainMenu },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            
            Screen.CreateGame -> {
                // TODO: Implement CreateGameScreen
                PlaceholderScreen(
                    title = "Create Game", 
                    subtitle = "Set up a new game lobby",
                    onBack = { currentScreen = Screen.MainMenu },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            
            Screen.GameLobby -> {
                // TODO: Implement GameLobbyScreen
                PlaceholderScreen(
                    title = "Game Lobby",
                    subtitle = "Waiting for players...",
                    onBack = { currentScreen = Screen.MainMenu },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            
            Screen.WordGame -> {
                WordGameScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = subtitle,
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "🚧 Coming Soon! 🚧",
            fontSize = 18.sp,
            color = Color(0xFF10B981),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Back to Main Menu")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BabblerAppPreview() {
    MaterialTheme {
        BabblerApp()
    }
}
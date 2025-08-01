package com.example.babbler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.MaterialTheme
import com.example.babbler.ui.screens.MainMenuScreen
import com.example.babbler.ui.screens.WordGameScreen

sealed class Screen {
    object MainMenu : Screen()
    object WordGame : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (currentScreen) {
            Screen.MainMenu -> {
                MainMenuScreen(
                    modifier = Modifier.padding(innerPadding),
                    onPlayOnline = {
                        // TODO: Navigate to finding game screen
                        // For now, go directly to word game (Step 1 testing)
                        currentScreen = Screen.WordGame
                    },
                    onPracticeMode = {
                        currentScreen = Screen.WordGame
                    }
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

@Preview(showBackground = true)
@Composable
fun BabblerAppPreview() {
    MaterialTheme {
        BabblerApp()
    }
}
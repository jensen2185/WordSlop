package com.wordslop.game.auth

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.wordslop.game.R
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Authentication with Google Sign-In
 */
class FirebaseAuthManager(private val context: Context) {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val oneTapClient: SignInClient = Identity.getSignInClient(context)
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    
    val currentUser: FirebaseUser?
        get() = auth.currentUser
    
    val isSignedIn: Boolean
        get() = currentUser != null
    
    /**
     * Start Google Sign-In flow
     */
    suspend fun startGoogleSignIn(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        return try {
            val signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                    BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        // Web client ID from Firebase console (auto-generated from google-services.json)
                        .setServerClientId(context.getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                )
                .setAutoSelectEnabled(true)
                .build()
            
            val result = oneTapClient.beginSignIn(signInRequest).await()
            val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
            launcher.launch(intentSenderRequest)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Handle Google Sign-In result
     */
    suspend fun handleSignInResult(data: Intent?): AuthResult {
        return try {
            val credential = oneTapClient.getSignInCredentialFromIntent(data)
            val idToken = credential.googleIdToken
            
            if (idToken != null) {
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(firebaseCredential).await()
                val user = result.user
                
                if (user != null) {
                    AuthResult.Success(
                        userId = user.uid,
                        displayName = user.displayName ?: "Unknown User",
                        email = user.email ?: ""
                    )
                } else {
                    AuthResult.Error("Authentication failed")
                }
            } else {
                AuthResult.Error("No ID token received")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AuthResult.Error(e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Sign in anonymously for guest users
     */
    suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user
            
            if (user != null) {
                AuthResult.Success(
                    userId = user.uid,
                    displayName = "Anonymous User",
                    email = ""
                )
            } else {
                AuthResult.Error("Anonymous authentication failed")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AuthResult.Error(e.message ?: "Anonymous authentication failed")
        }
    }
    
    /**
     * Save guest username for persistence
     */
    fun saveGuestUsername(userId: String, username: String) {
        prefs.edit().putString("guest_username_$userId", username).apply()
    }
    
    /**
     * Get saved guest username
     */
    fun getGuestUsername(userId: String): String? {
        return prefs.getString("guest_username_$userId", null)
    }
    
    /**
     * Sign out the current user
     */
    fun signOut() {
        // Clear guest username if signing out anonymous user
        currentUser?.let { user ->
            if (user.isAnonymous) {
                prefs.edit().remove("guest_username_${user.uid}").apply()
            }
        }
        auth.signOut()
        oneTapClient.signOut()
    }
    
    /**
     * Get current user info
     */
    fun getCurrentUserInfo(): UserInfo? {
        val user = currentUser ?: return null
        
        // For anonymous users, retrieve saved username
        val gameUsername = if (user.isAnonymous) {
            getGuestUsername(user.uid)
        } else {
            null
        }
        
        return UserInfo(
            userId = user.uid,
            displayName = if (user.isAnonymous && gameUsername != null) gameUsername else (user.displayName ?: "Unknown User"),
            email = user.email ?: "",
            gameUsername = gameUsername,
            isGuest = user.isAnonymous
        )
    }
}

/**
 * Authentication result sealed class
 */
sealed class AuthResult {
    data class Success(
        val userId: String,
        val displayName: String,
        val email: String
    ) : AuthResult()
    
    data class Error(val message: String) : AuthResult()
}

/**
 * User information data class
 */
data class UserInfo(
    val userId: String,
    val displayName: String,
    val email: String,
    val gameUsername: String? = null, // Custom username for the game
    val isGuest: Boolean = false
)

/**
 * Guest user creation
 */
fun createGuestUser(username: String): UserInfo {
    return UserInfo(
        userId = "guest_${System.currentTimeMillis()}_${(1000..9999).random()}",
        displayName = username,
        email = "",
        gameUsername = username,
        isGuest = true
    )
}
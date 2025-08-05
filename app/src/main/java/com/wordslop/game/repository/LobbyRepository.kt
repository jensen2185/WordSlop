package com.wordslop.game.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.wordslop.game.model.GameLobby
import com.wordslop.game.model.GameStatus
import com.wordslop.game.model.LobbyPlayer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.wordslop.game.utils.DebugLogger

class LobbyRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val lobbiesCollection = firestore.collection("game_lobbies")
    
    suspend fun createLobby(lobby: GameLobby): Result<String> {
        return try {
            println("DEBUG: Creating lobby ${lobby.gameId} with isPublic=${lobby.isPublic}, status=${lobby.gameStatus}")
            val lobbyData = lobby.toFirestoreMap()
            println("DEBUG: Lobby data to save: $lobbyData")
            lobbiesCollection.document(lobby.gameId).set(lobbyData).await()
            println("DEBUG: Successfully created lobby ${lobby.gameId}")
            
            // Verify the lobby was saved correctly
            val savedLobby = lobbiesCollection.document(lobby.gameId).get().await()
            if (savedLobby.exists()) {
                println("DEBUG: Verified lobby exists in Firestore: ${savedLobby.data}")
            } else {
                println("DEBUG: ERROR - Lobby not found in Firestore after creation!")
            }
            
            Result.success(lobby.gameId)
        } catch (e: Exception) {
            println("DEBUG: Failed to create lobby: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun joinLobby(lobbyId: String, player: LobbyPlayer): Result<Unit> {
        return try {
            DebugLogger.log("REPO: Attempting to join lobby $lobbyId with player ${player.username} (${player.userId})")
            
            firestore.runTransaction { transaction ->
                val lobbyRef = lobbiesCollection.document(lobbyId)
                val lobbySnapshot = transaction.get(lobbyRef)
                
                DebugLogger.log("REPO: Got lobby snapshot, exists: ${lobbySnapshot.exists()}")
                
                if (!lobbySnapshot.exists()) {
                    throw Exception("Lobby not found")
                }
                
                val lobby = lobbySnapshot.toGameLobby()
                DebugLogger.log("REPO: Lobby has ${lobby.players.size}/${lobby.maxPlayers} players, status: ${lobby.gameStatus}")
                
                if (lobby.players.size >= lobby.maxPlayers) {
                    throw Exception("Lobby is full")
                }
                
                if (lobby.players.any { it.userId == player.userId }) {
                    throw Exception("Player already in lobby")
                }
                
                // If game is in progress, join as spectator
                val playerToAdd = if (lobby.gameStatus == GameStatus.IN_PROGRESS) {
                    player.copy(isSpectator = true)
                } else {
                    player
                }
                
                val updatedPlayers = lobby.players + playerToAdd
                val updatedLobby = lobby.copy(players = updatedPlayers)
                
                DebugLogger.log("REPO: Adding player, new player count: ${updatedPlayers.size}")
                transaction.set(lobbyRef, updatedLobby.toFirestoreMap())
            }.await()
            
            DebugLogger.log("REPO: Successfully joined lobby $lobbyId")
            Result.success(Unit)
        } catch (e: Exception) {
            DebugLogger.log("REPO: Failed to join lobby - ${e.message}")
            Result.failure(e)
        }
    }
    
    fun getPublicLobbiesFlow(): Flow<List<GameLobby>> = callbackFlow {
        println("DEBUG: Setting up public lobbies listener")
        val listener = lobbiesCollection
            .whereEqualTo("isPublic", true)
            .whereIn("gameStatus", listOf(GameStatus.WAITING.name, GameStatus.IN_PROGRESS.name))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("DEBUG: Error in public lobbies listener: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                
                println("DEBUG: Public lobbies snapshot received, ${snapshot?.documents?.size ?: 0} documents")
                val lobbies = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        println("DEBUG: Processing lobby document: ${doc.id}")
                        val lobby = doc.toGameLobby()
                        println("DEBUG: Lobby parsed: ${lobby.gameId}, isPublic: ${lobby.isPublic}, status: ${lobby.gameStatus}")
                        lobby
                    } catch (e: Exception) {
                        println("DEBUG: Failed to parse lobby ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                
                println("DEBUG: Sending ${lobbies.size} public lobbies to UI")
                trySend(lobbies)
            }
        
        awaitClose { 
            println("DEBUG: Removing public lobbies listener")
            listener.remove() 
        }
    }
    
    fun getLobbyFlow(lobbyId: String): Flow<GameLobby?> = callbackFlow {
        val listener = lobbiesCollection.document(lobbyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val lobby = if (snapshot?.exists() == true) {
                    try {
                        snapshot.toGameLobby()
                    } catch (e: Exception) {
                        null
                    }
                } else null
                
                trySend(lobby)
            }
        
        awaitClose { listener.remove() }
    }
    
    suspend fun updateGameStatus(lobbyId: String, status: GameStatus): Result<Unit> {
        return try {
            lobbiesCollection.document(lobbyId)
                .update("gameStatus", status.name)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getLobbyById(lobbyId: String): Result<GameLobby?> {
        return try {
            val snapshot = lobbiesCollection.document(lobbyId).get().await()
            val lobby = if (snapshot.exists()) {
                snapshot.toGameLobby()
            } else null
            Result.success(lobby)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun leaveLobby(lobbyId: String, userId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val lobbyRef = lobbiesCollection.document(lobbyId)
                val lobbySnapshot = transaction.get(lobbyRef)
                
                if (!lobbySnapshot.exists()) {
                    throw Exception("Lobby not found")
                }
                
                val lobby = lobbySnapshot.toGameLobby()
                val updatedPlayers = lobby.players.filter { it.userId != userId }
                
                println("DEBUG LEAVE: Lobby ${lobbyId} had ${lobby.players.size} players, now has ${updatedPlayers.size}")
                
                if (updatedPlayers.isEmpty()) {
                    // Delete lobby if no players remain
                    println("DEBUG LEAVE: Deleting empty lobby ${lobbyId}")
                    transaction.delete(lobbyRef)
                } else {
                    // Reassign host if needed
                    var newHost = lobby.hostUserId
                    var newHostUsername = lobby.hostUsername
                    
                    if (lobby.hostUserId == userId && updatedPlayers.isNotEmpty()) {
                        newHost = updatedPlayers.first().userId
                        newHostUsername = updatedPlayers.first().username
                        println("DEBUG LEAVE: Reassigning host from ${lobby.hostUserId} to ${newHost}")
                    }
                    
                    val updatedLobby = lobby.copy(
                        players = updatedPlayers.map { player ->
                            if (player.userId == newHost) player.copy(isHost = true) else player.copy(isHost = false)
                        },
                        hostUserId = newHost,
                        hostUsername = newHostUsername
                    )
                    
                    transaction.set(lobbyRef, updatedLobby.toFirestoreMap())
                    println("DEBUG LEAVE: Updated lobby ${lobbyId} with ${updatedPlayers.size} players")
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Update player heartbeat - call this periodically for active players
    suspend fun updatePlayerHeartbeat(lobbyId: String, userId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val lobbyRef = lobbiesCollection.document(lobbyId)
                val lobbySnapshot = transaction.get(lobbyRef)
                
                if (!lobbySnapshot.exists()) {
                    return@runTransaction // Lobby doesn't exist, nothing to update
                }
                
                val lobby = lobbySnapshot.toGameLobby()
                val updatedPlayers = lobby.players.map { player ->
                    if (player.userId == userId) {
                        player.copy(joinedAt = System.currentTimeMillis()) // Use joinedAt as lastSeen timestamp
                    } else player
                }
                
                if (updatedPlayers != lobby.players) {
                    val updatedLobby = lobby.copy(players = updatedPlayers)
                    transaction.set(lobbyRef, updatedLobby.toFirestoreMap())
                    println("DEBUG HEARTBEAT: Updated heartbeat for user $userId in lobby $lobbyId")
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG HEARTBEAT: Failed to update heartbeat: ${e.message}")
            Result.failure(e)
        }
    }
    
    // Cleanup stale lobbies - removes inactive players and empty lobbies
    suspend fun cleanupOrphanedLobbies(): Result<Unit> {
        return try {
            println("DEBUG CLEANUP: Starting stale lobby cleanup")
            val lobbies = lobbiesCollection.get().await()
            var deletedCount = 0
            var playersRemovedCount = 0
            val currentTime = System.currentTimeMillis()
            val lobbyInactiveThreshold = 10 * 1000L // 10 seconds for all states now
            val gameInactiveThreshold = 10 * 1000L // 10 seconds for active games
            
            println("DEBUG CLEANUP: Found ${lobbies.documents.size} total lobbies")
            println("DEBUG CLEANUP: Current time: $currentTime")
            println("DEBUG CLEANUP: Lobby threshold: ${lobbyInactiveThreshold}ms, Game threshold: ${gameInactiveThreshold}ms")
            
            firestore.runBatch { batch ->
                lobbies.documents.forEachIndexed { index, doc ->
                    try {
                        val lobby = doc.toGameLobby()
                        println("DEBUG CLEANUP: [$index] Processing lobby ${lobby.gameId}")
                        println("DEBUG CLEANUP: [$index]   Host: ${lobby.hostUsername} (${lobby.hostUserId})")
                        println("DEBUG CLEANUP: [$index]   Status: ${lobby.gameStatus}")
                        println("DEBUG CLEANUP: [$index]   Players: ${lobby.players.size}")
                        
                        // Remove inactive players - use 10 second threshold for faster disconnect detection
                        val inactiveThreshold = gameInactiveThreshold // Always use 10 seconds for disconnect detection
                        
                        val activePlayers = lobby.players.filterIndexed { playerIndex, player ->
                            val timeSinceLastSeen = currentTime - player.joinedAt
                            val isActive = timeSinceLastSeen < inactiveThreshold
                            val secondsInactive = timeSinceLastSeen / 1000.0
                            
                            println("DEBUG CLEANUP: [$index][$playerIndex] Player: ${player.username} (${player.userId})")
                            println("DEBUG CLEANUP: [$index][$playerIndex]   joinedAt: ${player.joinedAt}")
                            println("DEBUG CLEANUP: [$index][$playerIndex]   timeSinceLastSeen: ${timeSinceLastSeen}ms")
                            println("DEBUG CLEANUP: [$index][$playerIndex]   secondsInactive: ${"%.1f".format(secondsInactive)}s")
                            println("DEBUG CLEANUP: [$index][$playerIndex]   isActive: $isActive (threshold: ${inactiveThreshold}ms, gameStatus: ${lobby.gameStatus})")
                            
                            if (!isActive) {
                                println("DEBUG CLEANUP: [$index][$playerIndex]   REMOVING INACTIVE PLAYER (${lobby.gameStatus})")
                                playersRemovedCount++
                            }
                            
                            isActive
                        }
                        
                        println("DEBUG CLEANUP: [$index] Active players after filter: ${activePlayers.size}/${lobby.players.size}")
                        
                        if (activePlayers.isEmpty()) {
                            // No active players - delete the entire lobby
                            println("DEBUG CLEANUP: [$index] DELETING EMPTY LOBBY ${lobby.gameId}")
                            batch.delete(doc.reference)
                            deletedCount++
                        } else if (activePlayers.size != lobby.players.size) {
                            // Some players were inactive - update lobby with only active players
                            var updatedLobby = lobby.copy(players = activePlayers)
                            
                            // If host was removed, reassign to first active player
                            if (activePlayers.none { it.userId == lobby.hostUserId }) {
                                val newHost = activePlayers.first()
                                updatedLobby = updatedLobby.copy(
                                    hostUserId = newHost.userId,
                                    hostUsername = newHost.username,
                                    players = activePlayers.map { player ->
                                        player.copy(isHost = player.userId == newHost.userId)
                                    }
                                )
                                println("DEBUG CLEANUP: [$index] REASSIGNING HOST to ${newHost.username}")
                            }
                            
                            batch.set(doc.reference, updatedLobby.toFirestoreMap())
                            println("DEBUG CLEANUP: [$index] UPDATING LOBBY - removed ${lobby.players.size - activePlayers.size} inactive players")
                        } else {
                            println("DEBUG CLEANUP: [$index] NO CHANGES NEEDED - all players active")
                        }
                    } catch (e: Exception) {
                        // Skip malformed lobbies
                        println("DEBUG CLEANUP: [$index] ERROR parsing lobby ${doc.id}: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }.await()
            
            println("DEBUG CLEANUP: ===== CLEANUP SUMMARY =====")
            println("DEBUG CLEANUP: Deleted $deletedCount lobbies")
            println("DEBUG CLEANUP: Removed $playersRemovedCount inactive players") 
            println("DEBUG CLEANUP: ===== END CLEANUP =====")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG CLEANUP: FATAL ERROR: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun updatePlayerReady(lobbyId: String, userId: String, isReady: Boolean): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val lobbyRef = lobbiesCollection.document(lobbyId)
                val lobbySnapshot = transaction.get(lobbyRef)
                
                if (!lobbySnapshot.exists()) {
                    throw Exception("Lobby not found")
                }
                
                val lobby = lobbySnapshot.toGameLobby()
                val updatedPlayers = lobby.players.map { player ->
                    if (player.userId == userId) player.copy(isReady = isReady) else player
                }
                
                val updatedLobby = lobby.copy(players = updatedPlayers)
                transaction.set(lobbyRef, updatedLobby.toFirestoreMap())
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateLobbyStatus(lobbyId: String, status: GameStatus): Result<Unit> {
        return try {
            lobbiesCollection.document(lobbyId)
                .update("gameStatus", status.name)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun promoteSpectatorsToPlayers(lobbyId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val lobbyRef = lobbiesCollection.document(lobbyId)
                val lobbySnapshot = transaction.get(lobbyRef)
                
                if (!lobbySnapshot.exists()) {
                    throw Exception("Lobby not found")
                }
                
                val lobby = lobbySnapshot.toGameLobby()
                val updatedPlayers = lobby.players.map { player ->
                    if (player.isSpectator) {
                        player.copy(isSpectator = false, isReady = false)
                    } else {
                        player.copy(isReady = false) // Reset ready status for new round
                    }
                }
                
                val updatedLobby = lobby.copy(players = updatedPlayers)
                transaction.set(lobbyRef, updatedLobby.toFirestoreMap())
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGameReadyStates(lobbyId: String): Result<Map<String, Map<String, Any>>> {
        return try {
            val snapshot = firestore.collection("game_ready_states").document(lobbyId).get().await()
            val states = if (snapshot.exists()) {
                snapshot.data?.filterValues { it is Map<*, *> }?.mapValues { (_, value) ->
                    @Suppress("UNCHECKED_CAST")
                    value as Map<String, Any>
                } ?: emptyMap()
            } else emptyMap()
            Result.success(states)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updatePlayerGameReady(lobbyId: String, userId: String, isReady: Boolean, selectedWords: List<String>): Result<Unit> {
        return try {
            val gameReadyRef = firestore.collection("game_ready_states").document(lobbyId)
            val userState = mapOf(
                "isReady" to isReady,
                "selectedWords" to selectedWords,
                "username" to getUsernameFromLobby(lobbyId, userId),
                "updatedAt" to System.currentTimeMillis()
            )
            gameReadyRef.update(userId, userState).await()
            Result.success(Unit)
        } catch (e: Exception) {
            // If document doesn't exist, create it
            try {
                val gameReadyRef = firestore.collection("game_ready_states").document(lobbyId)
                val userState = mapOf(
                    "isReady" to isReady,
                    "selectedWords" to selectedWords,
                    "username" to getUsernameFromLobby(lobbyId, userId),
                    "updatedAt" to System.currentTimeMillis()
                )
                gameReadyRef.set(mapOf(userId to userState)).await()
                Result.success(Unit)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }
    
    suspend fun submitVote(lobbyId: String, voterUserId: String, votedForUserId: String): Result<Unit> {
        return try {
            val votingRef = firestore.collection("voting_results").document(lobbyId)
            votingRef.update(voterUserId, votedForUserId).await()
            Result.success(Unit)
        } catch (e: Exception) {
            // If document doesn't exist, create it
            try {
                val votingRef = firestore.collection("voting_results").document(lobbyId)
                votingRef.set(mapOf(voterUserId to votedForUserId)).await()
                Result.success(Unit)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }
    
    suspend fun getVotingResults(lobbyId: String): Result<Map<String, String>> {
        return try {
            val snapshot = firestore.collection("voting_results").document(lobbyId).get().await()
            val votes = if (snapshot.exists()) {
                @Suppress("UNCHECKED_CAST")
                snapshot.data as? Map<String, String> ?: emptyMap()
            } else emptyMap()
            Result.success(votes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun clearVotingData(lobbyId: String): Result<Unit> {
        return try {
            firestore.runBatch { batch ->
                batch.delete(firestore.collection("game_ready_states").document(lobbyId))
                batch.delete(firestore.collection("voting_results").document(lobbyId))
                batch.delete(firestore.collection("sentence_emojis").document(lobbyId))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Emoji awarding functionality
    suspend fun awardEmoji(lobbyId: String, sentenceIndex: Int, emoji: String, awardedByUserId: String): Result<Unit> {
        return try {
            val emojiRef = firestore.collection("sentence_emojis").document(lobbyId)
            val emojiData = mapOf(
                "sentenceIndex" to sentenceIndex,
                "emoji" to emoji,
                "awardedBy" to awardedByUserId,
                "awardedAt" to System.currentTimeMillis()
            )
            
            // Use the sentence index and user ID as a compound key to allow one emoji per sentence per user
            val emojiKey = "${sentenceIndex}_${awardedByUserId}"
            emojiRef.update(emojiKey, emojiData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            // If document doesn't exist, create it
            try {
                val emojiRef = firestore.collection("sentence_emojis").document(lobbyId)
                val emojiKey = "${sentenceIndex}_${awardedByUserId}"
                val emojiData = mapOf(
                    "sentenceIndex" to sentenceIndex,
                    "emoji" to emoji,
                    "awardedBy" to awardedByUserId,
                    "awardedAt" to System.currentTimeMillis()
                )
                emojiRef.set(mapOf(emojiKey to emojiData)).await()
                Result.success(Unit)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }
    
    suspend fun getSentenceEmojis(lobbyId: String): Result<Map<Int, List<String>>> {
        return try {
            val snapshot = firestore.collection("sentence_emojis").document(lobbyId).get().await()
            val emojiMap = mutableMapOf<Int, MutableList<String>>()
            
            if (snapshot.exists()) {
                snapshot.data?.forEach { (key, value) ->
                    if (value is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val emojiData = value as Map<String, Any>
                        val sentenceIndex = emojiData["sentenceIndex"] as? Long ?: return@forEach
                        val emoji = emojiData["emoji"] as? String ?: return@forEach
                        
                        emojiMap.getOrPut(sentenceIndex.toInt()) { mutableListOf() }.add(emoji)
                    }
                }
            }
            
            // Convert to immutable map
            val result = emojiMap.mapValues { it.value.toList() }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun getUsernameFromLobby(lobbyId: String, userId: String): String {
        return try {
            val lobby = getLobbyById(lobbyId).getOrNull()
            lobby?.players?.find { it.userId == userId }?.username ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    suspend fun deleteAllLobbies(): Result<Unit> {
        return try {
            println("DEBUG: Deleting ALL lobbies to start fresh")
            val allLobbies = lobbiesCollection.get().await()
            firestore.runBatch { batch ->
                allLobbies.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
            }.await()
            println("DEBUG: Deleted ${allLobbies.documents.size} lobbies")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: Failed to delete lobbies: ${e.message}")
            Result.failure(e)
        }
    }
    
    // Query specific lobby by ID to see actual Firebase data
    suspend fun queryLobbyDirectly(lobbyId: String): Result<String> {
        return try {
            val doc = lobbiesCollection.document(lobbyId).get().await()
            val debugInfo = StringBuilder()
            val currentTime = System.currentTimeMillis()
            
            debugInfo.appendLine("=== DIRECT FIREBASE QUERY ===")
            debugInfo.appendLine("Lobby ID: $lobbyId")
            debugInfo.appendLine("Document exists: ${doc.exists()}")
            debugInfo.appendLine("Current time: $currentTime")
            
            if (doc.exists()) {
                debugInfo.appendLine("\n--- RAW FIRESTORE DATA ---")
                debugInfo.appendLine(doc.data.toString())
                
                try {
                    val lobby = doc.toGameLobby()
                    debugInfo.appendLine("\n--- PARSED LOBBY DATA ---")
                    debugInfo.appendLine("Game ID: ${lobby.gameId}")
                    debugInfo.appendLine("Host: ${lobby.hostUsername} (${lobby.hostUserId})")
                    debugInfo.appendLine("Status: ${lobby.gameStatus}")
                    debugInfo.appendLine("Is Public: ${lobby.isPublic}")
                    debugInfo.appendLine("Max Players: ${lobby.maxPlayers}")
                    debugInfo.appendLine("Players count: ${lobby.players.size}")
                    debugInfo.appendLine("Created at: ${lobby.createdAt}")
                    
                    debugInfo.appendLine("\n--- PLAYER DETAILS ---")
                    lobby.players.forEachIndexed { index, player ->
                        val timeSinceJoin = currentTime - player.joinedAt
                        val hoursInactive = timeSinceJoin / (60 * 60 * 1000.0)
                        debugInfo.appendLine("Player $index:")
                        debugInfo.appendLine("  Username: ${player.username}")
                        debugInfo.appendLine("  User ID: ${player.userId}")
                        debugInfo.appendLine("  Is Host: ${player.isHost}")
                        debugInfo.appendLine("  Is Ready: ${player.isReady}")
                        debugInfo.appendLine("  Joined At: ${player.joinedAt}")
                        debugInfo.appendLine("  Hours Inactive: ${"%.2f".format(hoursInactive)}")
                    }
                } catch (e: Exception) {
                    debugInfo.appendLine("\n--- PARSING ERROR ---")
                    debugInfo.appendLine("Error: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                debugInfo.appendLine("Lobby does not exist in Firebase!")
            }
            
            val result = debugInfo.toString()
            println("FIREBASE QUERY RESULT:\n$result")
            Result.success(result)
        } catch (e: Exception) {
            val error = "Failed to query lobby $lobbyId: ${e.message}"
            println("FIREBASE QUERY ERROR: $error")
            Result.failure(e)
        }
    }
    
    // Online presence tracking for testing coordination
    suspend fun updateUserPresence(userId: String, username: String): Result<Unit> {
        return try {
            val presenceData = mapOf(
                "userId" to userId,
                "username" to username,
                "lastSeen" to System.currentTimeMillis()
            )
            firestore.collection("user_presence").document(userId).set(presenceData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun removeUserPresence(userId: String): Result<Unit> {
        return try {
            firestore.collection("user_presence").document(userId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getOnlineUsersFlow(): Flow<List<Map<String, Any>>> = callbackFlow {
        var listener: ListenerRegistration? = null
        
        try {
            listener = firestore.collection("user_presence")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        println("DEBUG PRESENCE: Error getting online users: ${error.message}")
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    
                    val currentTime = System.currentTimeMillis()
                    val onlineThreshold = 60000L // 60 seconds
                    
                    val onlineUsers = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            val data = doc.data
                            val lastSeen = data?.get("lastSeen") as? Long ?: 0L
                            
                            // Only include users seen within the last 60 seconds
                            if (currentTime - lastSeen < onlineThreshold) {
                                data
                            } else {
                                // Clean up stale entries
                                try {
                                    doc.reference.delete()
                                } catch (e: Exception) {
                                    // Ignore cleanup errors
                                }
                                null
                            }
                        } catch (e: Exception) {
                            println("DEBUG PRESENCE: Error processing user doc: ${e.message}")
                            null
                        }
                    } ?: emptyList()
                    
                    trySend(onlineUsers)
                }
        } catch (e: Exception) {
            println("DEBUG PRESENCE: Error setting up listener: ${e.message}")
            trySend(emptyList())
        }
        
        awaitClose { 
            try {
                listener?.remove()
            } catch (e: Exception) {
                println("DEBUG PRESENCE: Error removing listener: ${e.message}")
            }
        }
    }
    
    suspend fun getOnlineUserCount(): Result<Int> {
        return try {
            val snapshot = firestore.collection("user_presence").get().await()
            val currentTime = System.currentTimeMillis()
            val onlineThreshold = 60000L // 60 seconds
            
            val onlineCount = snapshot.documents.count { doc ->
                val lastSeen = doc.data?.get("lastSeen") as? Long ?: 0L
                val isOnline = currentTime - lastSeen < onlineThreshold
                
                // Clean up stale entries
                if (!isOnline) {
                    doc.reference.delete()
                }
                
                isOnline
            }
            
            Result.success(onlineCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Debug function to inspect all lobby data  
    suspend fun debugAllLobbies(): Result<String> {
        return try {
            val lobbies = lobbiesCollection.get().await()
            val debugInfo = StringBuilder()
            val currentTime = System.currentTimeMillis()
            
            debugInfo.appendLine("=== ALL LOBBIES DEBUG ===")
            debugInfo.appendLine("Current time: $currentTime")
            debugInfo.appendLine("Found ${lobbies.documents.size} lobbies in Firebase")
            
            lobbies.documents.forEachIndexed { index, doc ->
                debugInfo.appendLine("\n--- LOBBY $index ---")
                debugInfo.appendLine("Document ID: ${doc.id}")
                
                try {
                    val lobby = doc.toGameLobby()
                    debugInfo.appendLine("Game ID: ${lobby.gameId}")
                    debugInfo.appendLine("Host: ${lobby.hostUsername}")
                    debugInfo.appendLine("Status: ${lobby.gameStatus}")
                    debugInfo.appendLine("Players: ${lobby.players.size}")
                    
                    lobby.players.forEach { player ->
                        val timeDiff = currentTime - player.joinedAt
                        val hoursInactive = timeDiff / (60 * 60 * 1000.0)
                        debugInfo.appendLine("  - ${player.username}:")
                        debugInfo.appendLine("    joinedAt: ${player.joinedAt}")
                        debugInfo.appendLine("    currentTime: $currentTime")
                        debugInfo.appendLine("    timeDiff: ${timeDiff}ms")
                        debugInfo.appendLine("    hoursInactive: ${"%.2f".format(hoursInactive)}h")
                        debugInfo.appendLine("    isActive: ${timeDiff < 60000}")
                    }
                } catch (e: Exception) {
                    debugInfo.appendLine("ERROR parsing: ${e.message}")
                }
            }
            
            val result = debugInfo.toString()
            println("ALL LOBBIES DEBUG:\n$result")
            Result.success(result)
        } catch (e: Exception) {
            val error = "Failed to debug all lobbies: ${e.message}"
            println("DEBUG ALL LOBBIES ERROR: $error")
            Result.failure(e)
        }
    }
}

private fun GameLobby.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "gameId" to gameId,
        "hostUserId" to hostUserId,
        "hostUsername" to hostUsername,
        "isPublic" to isPublic,
        "passcode" to (passcode ?: ""),
        "maxPlayers" to maxPlayers,
        "numberOfRounds" to numberOfRounds,
        "players" to players.map { it.toFirestoreMap() },
        "gameStatus" to gameStatus.name,
        "createdAt" to createdAt
    )
}

private fun LobbyPlayer.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "userId" to userId,
        "username" to username,
        "isReady" to isReady,
        "isHost" to isHost,
        "isSpectator" to isSpectator,
        "joinedAt" to joinedAt
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toGameLobby(): GameLobby {
    val data = this.data ?: throw Exception("Document data is null")
    
    val playersData = data["players"] as? List<Map<String, Any>> ?: emptyList()
    val players = playersData.map { playerData ->
        LobbyPlayer(
            userId = playerData["userId"] as String,
            username = playerData["username"] as String,
            isReady = playerData["isReady"] as Boolean,
            isHost = playerData["isHost"] as Boolean,
            isSpectator = playerData["isSpectator"] as? Boolean ?: false,
            joinedAt = (playerData["joinedAt"] as? Long) ?: System.currentTimeMillis()
        )
    }
    
    return GameLobby(
        gameId = data["gameId"] as String,
        hostUserId = data["hostUserId"] as String,
        hostUsername = data["hostUsername"] as String,
        isPublic = data["isPublic"] as Boolean,
        passcode = (data["passcode"] as? String)?.takeIf { it.isNotEmpty() },
        maxPlayers = (data["maxPlayers"] as Long).toInt(),
        numberOfRounds = (data["numberOfRounds"] as Long).toInt(),
        players = players,
        gameStatus = GameStatus.valueOf(data["gameStatus"] as String),
        createdAt = (data["createdAt"] as? Long) ?: System.currentTimeMillis()
    )
}
package com.wordslop.game.model

/**
 * Game mode enumeration
 */
enum class GameMode {
    TESTING,    // Testing mode with CPU players
    ONLINE      // Online multiplayer mode with real players only
}

/**
 * Represents a game lobby where players wait before starting
 */
data class GameLobby(
    val gameId: String,
    val hostUserId: String,
    val hostUsername: String,
    val isPublic: Boolean = true,
    val passcode: String? = null,
    val maxPlayers: Int = 6,
    val numberOfRounds: Int = 3,
    val players: List<LobbyPlayer> = emptyList(),
    val gameStatus: GameStatus = GameStatus.WAITING,
    val gameMode: GameMode = GameMode.ONLINE,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Represents a player in the game lobby
 */
data class LobbyPlayer(
    val userId: String,
    val username: String,
    val isReady: Boolean = false,
    val isHost: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis()
)

/**
 * Game status enumeration
 */
enum class GameStatus {
    WAITING,    // Lobby is open, waiting for players
    STARTING,   // Game is about to start
    IN_PROGRESS, // Game is currently being played
    FINISHED    // Game has ended
}

/**
 * Game settings for creating a new game
 */
data class GameSettings(
    val isPublic: Boolean = true,
    val passcode: String? = null,
    val numberOfRounds: Int = 3,
    val maxPlayers: Int = 6,
    val gameMode: GameMode = GameMode.ONLINE
)

/**
 * Represents a player in the actual word game (converted from LobbyPlayer)
 */
data class GamePlayer(
    val userId: String,
    val username: String,
    val isReady: Boolean = false,
    val selectedWords: List<String> = emptyList(),
    val points: Int = 0,
    val isCurrentUser: Boolean = false
)
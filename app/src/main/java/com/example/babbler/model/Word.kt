package com.example.wordslop.model

/**
 * Represents the type of a word in the game
 */
enum class WordType {
    NOUN,
    VERB,
    ADJECTIVE,
    CONJUNCTION,
    PREPOSITION,
    ARTICLE,
    PRONOUN
}

/**
 * Represents a word in the arrangement game
 */
data class Word(
    val id: Int,
    val text: String,
    val type: WordType,
    val isPlaced: Boolean = false
)
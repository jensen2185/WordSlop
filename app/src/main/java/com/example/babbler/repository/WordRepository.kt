package com.example.babbler.repository

import com.example.babbler.model.Word
import com.example.babbler.model.WordType

/**
 * Repository for managing words in the game
 */
class WordRepository {
    
    private val nouns = listOf(
        "cat", "dog", "house", "car", "tree", "book", "phone", "computer", 
        "table", "chair", "window", "door", "water", "food", "music", 
        "picture", "flower", "mountain", "ocean", "star", "moon", "sun",
        "bird", "fish", "garden", "school", "friend", "family", "time"
    )
    
    private val verbs = listOf(
        "run", "jump", "sing", "dance", "read", "write", "play", "walk",
        "swim", "fly", "laugh", "cry", "sleep", "eat", "drink", "think",
        "speak", "listen", "watch", "learn", "teach", "build", "create",
        "explore", "discover", "travel", "climb", "draw", "paint"
    )
    
    private val adjectives = listOf(
        "big", "small", "happy", "sad", "fast", "slow", "hot", "cold",
        "bright", "dark", "loud", "quiet", "smooth", "rough", "soft",
        "hard", "sweet", "sour", "beautiful", "ugly", "strong", "weak",
        "tall", "short", "wide", "narrow", "clean", "dirty", "fresh"
    )
    
    private val conjunctions = listOf(
        "and", "or", "but", "so", "yet", "for", "nor", "because", "since", 
        "although", "while", "when", "if", "unless", "until"
    )
    
    private val prepositions = listOf(
        "in", "on", "at", "by", "for", "with", "to", "from", "of", "about",
        "under", "over", "through", "during", "before", "after", "between", "among"
    )
    
    private val articles = listOf(
        "a", "an", "the"
    )
    
    private val pronouns = listOf(
        "he", "she", "it", "they", "we", "you", "I", "me", "him", "her",
        "them", "us", "my", "your", "his", "her", "its", "our", "their"
    )
    
    /**
     * Generates 20 random words with a mix of all word types
     */
    fun getRandomWords(): List<Word> {
        val selectedWords = mutableListOf<Word>()
        var id = 0
        
        // Get 6 nouns
        val shuffledNouns = nouns.shuffled().take(6)
        shuffledNouns.forEach { word ->
            selectedWords.add(Word(id++, word, WordType.NOUN))
        }
        
        // Get 4 verbs
        val shuffledVerbs = verbs.shuffled().take(4)
        shuffledVerbs.forEach { word ->
            selectedWords.add(Word(id++, word, WordType.VERB))
        }
        
        // Get 4 adjectives
        val shuffledAdjectives = adjectives.shuffled().take(4)
        shuffledAdjectives.forEach { word ->
            selectedWords.add(Word(id++, word, WordType.ADJECTIVE))
        }
        
        // Get 2 conjunctions
        val shuffledConjunctions = conjunctions.shuffled().take(2)
        shuffledConjunctions.forEach { word ->
            selectedWords.add(Word(id++, word, WordType.CONJUNCTION))
        }
        
        // Get 2 prepositions
        val shuffledPrepositions = prepositions.shuffled().take(2)
        shuffledPrepositions.forEach { word ->
            selectedWords.add(Word(id++, word, WordType.PREPOSITION))
        }
        
        // Get 1 article
        val shuffledArticles = articles.shuffled().take(1)
        shuffledArticles.forEach { word ->
            selectedWords.add(Word(id++, word, WordType.ARTICLE))
        }
        
        // Get 1 pronoun
        val shuffledPronouns = pronouns.shuffled().take(1)
        shuffledPronouns.forEach { word ->
            selectedWords.add(Word(id++, word, WordType.PRONOUN))
        }
        
        // Shuffle the final list and return exactly 20 words
        return selectedWords.shuffled()
    }
    
    /**
     * Gets the 8 special always-present words
     */
    fun getSpecialWords(): List<Word> {
        return listOf(
            Word(1000, "a", WordType.ARTICLE),
            Word(1001, "s", WordType.NOUN), // For plurals
            Word(1002, "'s", WordType.NOUN), // For possessives (special styling)
            Word(1003, "as", WordType.CONJUNCTION),
            Word(1004, "to", WordType.PREPOSITION),
            Word(1005, "in", WordType.PREPOSITION),
            Word(1006, "the", WordType.ARTICLE),
            Word(1007, "er", WordType.NOUN) // For comparatives/agent nouns (special styling)
        )
    }
}
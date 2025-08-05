package com.wordslop.game.utils

import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object DebugLogger {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null
    
    fun init(context: android.content.Context) {
        try {
            // Use internal storage - no permissions needed
            val logsDir = File(context.filesDir, "logs")
            logsDir.mkdirs()
            logFile = File(logsDir, "debug.txt")
            
            // Clear old logs and start fresh
            logFile?.writeText("=== DEBUG LOG STARTED ${Date()} ===\n")
            log("Logger initialized at: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            println("Failed to init debug logger: ${e.message}")
        }
    }
    
    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message"
        
        // Always print to console too
        println(logMessage)
        
        // Also write to file
        try {
            logFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    writer.appendLine(logMessage)
                }
            }
        } catch (e: Exception) {
            println("Failed to write to log file: ${e.message}")
        }
    }
    
    fun getLogPath(): String? {
        return logFile?.absolutePath
    }
}
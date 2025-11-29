package com.example.invisibleinsight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceChatManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isWakeWordDetected = false
    private val handler = Handler(Looper.getMainLooper())
    
    var onPromptReceived: ((String) -> Unit)? = null
    var onWakeWordDetected: (() -> Unit)? = null 
    
    private val restartRunnable = Runnable { 
        if (!isListening) startListening() 
    }
    
    private val timeoutRunnable = Runnable {
        if (isWakeWordDetected) {
            Log.d("Nova", "Wake word timeout. Resetting.")
            isWakeWordDetected = false
        }
    }

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    isListening = false
                }
                
                override fun onError(error: Int) {
                    isListening = false
                    // Restart after a short delay to avoid busy loops
                    // Error 8 is ERROR_RECOGNIZER_BUSY, we should wait a bit
                    if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                         handler.postDelayed(restartRunnable, 500)
                    } else {
                         handler.postDelayed(restartRunnable, 1000)
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        processText(text)
                    }
                    // Immediately restart listening
                    handler.postDelayed(restartRunnable, 100)
                }
                
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Log.e("Nova", "Speech recognition not available on device")
        }
    }

    private fun processText(text: String) {
        val lower = text.lowercase()
        
        if (isWakeWordDetected) {
            Log.d("Nova", "Prompt captured: $text")
            handler.removeCallbacks(timeoutRunnable)
            isWakeWordDetected = false
            onPromptReceived?.invoke(text)
        } else {
            if (lower.contains("nova")) {
                Log.d("Nova", "Wake word 'Nova' detected in: '$text'")
                isWakeWordDetected = true
                onWakeWordDetected?.invoke()
                
                val parts = lower.split("nova", limit = 2)
                if (parts.size > 1 && parts[1].trim().isNotEmpty()) {
                    val prompt = parts[1].trim()
                    Log.d("Nova", "Immediate prompt detected: $prompt")
                    isWakeWordDetected = false
                    onPromptReceived?.invoke(prompt)
                } else {
                    Log.d("Nova", "Waiting for command...")
                    handler.postDelayed(timeoutRunnable, 4500)
                }
            }
        }
    }

    fun startListening() {
        if (isListening || speechRecognizer == null) return
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Keep listening for as long as possible (Android limits this but we request max)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
        }
        
        try {
            isListening = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("Nova", "Error starting listening: ${e.message}")
            isListening = false
            handler.postDelayed(restartRunnable, 1000)
        }
    }

    fun stopListening() {
        handler.removeCallbacksAndMessages(null)
        isListening = false
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

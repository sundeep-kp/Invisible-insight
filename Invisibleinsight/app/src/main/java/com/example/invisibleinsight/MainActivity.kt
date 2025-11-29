package com.example.invisibleinsight

import android.app.Activity
import android.content.Context // Added Context import
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.invisibleinsight.BuildConfig

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private var llamaContext: Long = 0
    private var voiceManager: VoiceChatManager? = null
    
    private var currentMode = "GYRO"
    private var currentLevel = 1

    private val settingsLauncher: ActivityResultLauncher<Intent> = 
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    val showMaze = intent.getBooleanExtra("showMaze", gameView.showMaze)
                    val showArrowKeys = intent.getBooleanExtra("showArrowKeys", gameView.showArrowKeys)
                    val controlModeAbsolute = intent.getBooleanExtra("controlModeAbsolute", gameView.getControlMode())
                    val sensitivity = intent.getIntExtra("sensitivity", gameView.getSensitivity())
                    val vibrationIntensity = intent.getIntExtra("vibrationIntensity", gameView.getVibration())
                    val spriteStyle = intent.getIntExtra("spriteStyle", gameView.getSpriteStyle())

                    gameView.updateSettings(showMaze, showArrowKeys, controlModeAbsolute, sensitivity, vibrationIntensity, spriteStyle)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (BuildConfig.ENABLE_CHATBOT) {
            voiceManager = VoiceChatManager(this).apply {
                onWakeWordDetected = { gameView.speak("Yes?") }
                onPromptReceived = { userText -> processVoiceInput(userText) }
            }
        }

        gameView = GameView(this, settingsLauncher)
        setContentView(gameView)
        
        val initialMode = intent.getStringExtra("initialMode") ?: "GYRO"
        currentMode = initialMode
        currentLevel = intent.getIntExtra("initialLevel", 1)
        
        val isTouchMode = initialMode == "TOUCH"
        val isAbsolute = initialMode == "ABSOLUTE"
        
        gameView.loadSettingsAndApply(isTouchMode)
        gameView.setLevel(currentLevel)
        
        if (isAbsolute) {
            gameView.setAbsoluteControlMode(true)
        }

        if (BuildConfig.ENABLE_CHATBOT) {
            lifecycleScope.launch(Dispatchers.IO) { prepareLlamaModel() }
            checkVoicePermission()
        }
    }
    
    fun onLevelComplete(elapsedTime: Long) {
        val prefs = getSharedPreferences("GameProgress", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val key = "best_time_${currentMode}_$currentLevel"
        val currentBest = prefs.getLong(key, Long.MAX_VALUE)
        if (elapsedTime < currentBest) {
            editor.putLong(key, elapsedTime)
            Log.d("GameProgress", "New best time for $key: $elapsedTime")
        }
        
        val nextLevel = currentLevel + 1
        if (nextLevel <= 3) {
            editor.putBoolean("unlocked_${currentMode}_$nextLevel", true)
        }
        
        if (currentMode == "GYRO" && currentLevel == 1) {
            editor.putBoolean("unlocked_TOUCH_1", true)
            editor.putBoolean("unlocked_ABSOLUTE_1", true)
        }
        
        editor.apply()
    }

    private fun checkVoicePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 100)
        } else {
            voiceManager?.startListening()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            voiceManager?.startListening()
        }
    }

    private fun processVoiceInput(text: String) {
        val response = KnowledgeBase.search(text)
        if (response != null) {
            var finalResponse = response
            if (response.contains("TIME_PLACEHOLDER")) {
                finalResponse = response.replace("TIME_PLACEHOLDER", SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()))
            }
            if (response.contains("DATE_PLACEHOLDER")) {
                finalResponse = response.replace("DATE_PLACEHOLDER", SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()))
            }
            gameView.speak(finalResponse)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val aiResponse = if (llamaContext != 0L) {
                val result = LlamaCpp.generate(llamaContext, "$text")
                if (result.trim().isEmpty()) "I have nothing to say." else result
            } else {
                "I heard: $text. My brain is missing."
            }
            withContext(Dispatchers.Main) { gameView.speak(aiResponse) }
        }
    }

    private fun prepareLlamaModel() {
        val modelName = "model.gguf"
        val file = File(filesDir, modelName)
        if (!file.exists()) {
            try {
                assets.open(modelName).use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
            } catch (e: Exception) { return }
        }
        if (file.exists()) {
            llamaContext = LlamaCpp.init(file.absolutePath)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (llamaContext != 0L) {
            LlamaCpp.free(llamaContext)
            llamaContext = 0
        }
        voiceManager?.destroy()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
        if (BuildConfig.ENABLE_CHATBOT && ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceManager?.startListening()
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        voiceManager?.stopListening()
    }
}

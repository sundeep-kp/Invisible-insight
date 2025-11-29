package com.example.invisibleinsight

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

class SettingsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        tts = TextToSpeech(this, this)

        val prefs = getSharedPreferences("GameSettings", Context.MODE_PRIVATE)

        val mazeVisibilitySwitch: SwitchMaterial = findViewById(R.id.maze_visibility_switch)
        val arrowKeysSwitch: SwitchMaterial = findViewById(R.id.arrow_keys_switch)
        val controlModeSwitch: SwitchMaterial = findViewById(R.id.control_mode_switch)
        val chatbotSwitch: SwitchMaterial = findViewById(R.id.chatbot_switch)
        val sensitivitySeekBar: SeekBar = findViewById(R.id.sensitivity_seekbar)
        val vibrationSeekBar: SeekBar = findViewById(R.id.vibration_seekbar)
        val spriteSeekBar: SeekBar = findViewById(R.id.sprite_seekbar)

        // Load current settings
        mazeVisibilitySwitch.isChecked = prefs.getBoolean("showMaze", false)
        arrowKeysSwitch.isChecked = prefs.getBoolean("showArrowKeys", false)
        controlModeSwitch.isChecked = prefs.getBoolean("controlModeAbsolute", false)
        chatbotSwitch.isChecked = prefs.getBoolean("enableChatbot", true)
        sensitivitySeekBar.progress = prefs.getInt("sensitivity", 50)
        vibrationSeekBar.progress = prefs.getInt("vibrationIntensity", 50)
        spriteSeekBar.progress = prefs.getInt("spriteStyle", 0)

        val resultIntent = Intent()
        val editor = prefs.edit()

        mazeVisibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("showMaze", isChecked).apply()
            resultIntent.putExtra("showMaze", isChecked)
            setResult(Activity.RESULT_OK, resultIntent)
            speak(if (isChecked) "Maze Visible" else "Maze Hidden")
        }

        arrowKeysSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("showArrowKeys", isChecked).apply()
            resultIntent.putExtra("showArrowKeys", isChecked)
            setResult(Activity.RESULT_OK, resultIntent)
            speak(if (isChecked) "Touch Mode Enabled" else "Gyro Mode Enabled")
        }

        controlModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("controlModeAbsolute", isChecked).apply()
            resultIntent.putExtra("controlModeAbsolute", isChecked)
            setResult(Activity.RESULT_OK, resultIntent)
            speak(if (isChecked) "Absolute Control Mode" else "Velocity Control Mode")
        }
        
        chatbotSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("enableChatbot", isChecked).apply()
            resultIntent.putExtra("enableChatbot", isChecked)
            setResult(Activity.RESULT_OK, resultIntent)
            speak(if (isChecked) "Chatbot Enabled" else "Chatbot Disabled")
        }

        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editor.putInt("sensitivity", progress).apply()
                resultIntent.putExtra("sensitivity", progress)
                setResult(Activity.RESULT_OK, resultIntent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                speak("Sensitivity ${sensitivitySeekBar.progress} percent")
            }
        })

        vibrationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editor.putInt("vibrationIntensity", progress).apply()
                resultIntent.putExtra("vibrationIntensity", progress)
                setResult(Activity.RESULT_OK, resultIntent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                speak("Vibration ${vibrationSeekBar.progress} percent")
            }
        })
        
        spriteSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editor.putInt("spriteStyle", progress).apply()
                resultIntent.putExtra("spriteStyle", progress)
                setResult(Activity.RESULT_OK, resultIntent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val styleName = when(spriteSeekBar.progress) {
                    0 -> "Cyan Circle"
                    1 -> "Pink Square"
                    2 -> "Gold Triangle"
                    3 -> "Ghost"
                    else -> "Unknown"
                }
                speak("Sprite Style: $styleName")
            }
        })
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
            speak("Settings Menu")
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}

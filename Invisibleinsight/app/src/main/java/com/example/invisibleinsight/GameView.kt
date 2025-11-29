package com.example.invisibleinsight

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.result.ActivityResultLauncher
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class GameState {
    WAITING,
    PLAYING,
    LEVEL_TRANSITION,
    GAME_OVER,
    WIN
}

class GameView(
    context: Context,
    private val settingsLauncher: ActivityResultLauncher<Intent>
) : SurfaceView(context), Runnable, TextToSpeech.OnInitListener {

    private var gameThread: Thread? = null
    @Volatile
    private var running = false
    private val paint = Paint()

    private var gameState = GameState.WAITING
    var showMaze = true
    var showArrowKeys = false // Re-purposed as "Touch Mode"
    var enableChatbot = true

    private val maze = Maze()
    private val player = Player(maze)
    private val inputController = InputController(context, player)
    private val prefs: SharedPreferences = context.getSharedPreferences("GameSettings", Context.MODE_PRIVATE)
    private val networkManager = NetworkManager()
    
    // Sonar
    private val sonar = DirectionalSonar()
    
    // TTS
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Settings
    private var vibrationIntensity = 50

    // Timer & Score
    private var startTime = 0L
    private var elapsedTime = 0L
    private var highScore = Long.MAX_VALUE
    
    // Lives System
    private var lives = 5
    private val maxLives = 5

    // Pulsing Vibration State
    private var lastVibrationTime = 0L
    private var isVibratingContinuous = false
    
    // Network throttle
    private var networkFrameCount = 0
    
    // Camera Offset for Scrolling
    private var cameraOffsetX = 0f

    // UI Rects
    private var settingsButtonRect = RectF()
    private var calibrateButtonRect = RectF()

    init {
        highScore = prefs.getLong("highScore", Long.MAX_VALUE)
        tts = TextToSpeech(context, this)

        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (gameThread == null) resume()
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, width: Int, height: Int) {
                synchronized(maze) {
                    maze.updateScale(width, height)
                    player.radius = maze.cellSize / 4f
                }
                settingsButtonRect = RectF(20f, 20f, 220f, 120f)
                calibrateButtonRect = RectF(width - 240f, 20f, width - 20f, 120f)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                pause()
                tts?.shutdown()
            }
        })
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
            // Start tutorial only if waiting, chatbot enabled, and it is Level 1
            if (gameState == GameState.WAITING && enableChatbot && maze.getCurrentLevel() == 1) {
                playTutorialSequence()
            }
        }
    }
    
    private fun playTutorialSequence() {
        if (!enableChatbot) return
        
        Thread {
            try {
                Thread.sleep(1000) 
                
                speak("Welcome to Invisible Insight.")
                Thread.sleep(2500)
                
                // Series-Specific Intro based on mode
                if (showArrowKeys) {
                    speak("Touch Mode. Tap the screen to guide the spirit.")
                } else if (inputController.controlModeAbsolute) {
                    speak("Absolute Mode. Tilt your device. The spirit's position mirrors your tilt angle.")
                } else {
                    speak("Gyro Mode. Tilt your device to move.")
                }
                Thread.sleep(3500)
                
                speak("Tap anywhere to start.")
                Thread.sleep(2500)
                
                speak("Listen carefully.")
                Thread.sleep(2000)
                
                speak("Follow this sound to win.")
                Thread.sleep(2000) 
                sonar.playTestTone(4, 2000) 
                Thread.sleep(500) 
                
                speak("This sound means a wall is on your right.")
                Thread.sleep(3000)
                sonar.playTestTone(1, 1500) 
                Thread.sleep(500)
                
                speak("This means wall on left.")
                Thread.sleep(2500)
                sonar.playTestTone(3, 1500) 
                Thread.sleep(500)
                
                speak("Wall above.")
                Thread.sleep(1500)
                sonar.playTestTone(0, 1500) 
                Thread.sleep(500)
                
                speak("Wall below.")
                Thread.sleep(1500)
                sonar.playTestTone(2, 1500) 
                Thread.sleep(500)
                
                speak("Good luck. Say Nova to ask for help.")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
    
    fun speak(text: String) {
        if (ttsReady && enableChatbot) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }
    
    fun setLevel(level: Int) {
        synchronized(maze) {
            maze.loadLevel(level)
            maze.updateScale(width, height)
            player.radius = maze.cellSize / 4f
            player.reset()
            cameraOffsetX = 0f
        }
    }
    
    fun setAbsoluteControlMode(enabled: Boolean) {
        inputController.controlModeAbsolute = enabled
    }

    override fun run() {
        while (running) {
            if (gameState == GameState.PLAYING) {
                update()
            }
            draw()
        }
    }

    private fun update() {
        synchronized(maze) {
            player.update()
            
            // Camera Logic for Level 3
            if (maze.getCurrentLevel() == 3) {
                val targetOffset = player.x - width / 2f
                val maxOffset = maze.mapWidth - width
                cameraOffsetX = targetOffset.coerceIn(0f, maxOffset)
            } else {
                cameraOffsetX = 0f
            }
            
            elapsedTime = System.currentTimeMillis() - startTime
            
            networkFrameCount++
            if (networkFrameCount >= 4) {
                networkManager.broadcastPosition(player.x, player.y)
                networkFrameCount = 0
            }
            
            if (!sonar.isTesting) {
                updateSonarFeedback()
                updateBeaconFeedback()
                updateSirenFeedback()
            }

            if (maze.isWall(player.x, player.y) || maze.isSpike(player.x, player.y)) {
                lives--
                vibrateOnce(200)
                
                if (lives > 0) {
                    speak("Ouch. Lost a life.")
                    val checkpoint = maze.getNearestCheckpoint(player.x, player.y)
                    player.x = checkpoint.x
                    player.y = checkpoint.y
                    player.setVelocity(0f, 0f)
                    if (!showArrowKeys) inputController.calibrate()
                } else {
                    speak("Game Over.")
                    gameState = GameState.GAME_OVER
                }
            } else if (maze.isWin(player.x, player.y)) {
                vibrateOnce(500)
                
                // Notify MainActivity safely
                if (context is MainActivity) {
                    // Temporarily commented out to fix build
                    // (context as MainActivity).onLevelComplete(elapsedTime)
                }
                
                if (maze.nextLevel()) {
                    speak("Level Complete.")
                    maze.updateScale(width, height)
                    player.radius = maze.cellSize / 4f
                    player.reset()
                    cameraOffsetX = 0f
                    startTime = System.currentTimeMillis() 
                    gameState = GameState.LEVEL_TRANSITION
                } else {
                    speak("You Win! Amazing.")
                    gameState = GameState.WIN
                    if (elapsedTime < highScore) {
                        highScore = elapsedTime
                        prefs.edit().putLong("highScore", highScore).apply()
                    }
                }
                if (!showArrowKeys) inputController.calibrate()
            } else {
                handleVibration()
            }
        }
    }
    
    private fun updateSonarFeedback() {
        val maxDist = maze.cellSize * 5f
        
        val distUp = maze.raycast(player.x, player.y, 0f, -1f, maxDist)
        val distRight = maze.raycast(player.x, player.y, 1f, 0f, maxDist)
        val distDown = maze.raycast(player.x, player.y, 0f, 1f, maxDist)
        val distLeft = maze.raycast(player.x, player.y, -1f, 0f, maxDist)
        
        fun mapDistToVol(dist: Float): Float {
            val normalized = (1f - (dist / maxDist)).coerceIn(0f, 1f)
            return normalized.pow(3) 
        }

        sonar.volumes[0] = mapDistToVol(distUp)
        sonar.volumes[1] = mapDistToVol(distRight)
        sonar.volumes[2] = mapDistToVol(distDown)
        sonar.volumes[3] = mapDistToVol(distLeft)
    }
    
    private fun updateBeaconFeedback() {
        val goal = maze.getWinPos()
        val dx = goal.x - player.x
        val dy = goal.y - player.y 
        
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val maxDist = sqrt((width * width + height * height).toDouble()).toFloat()
        
        val vol = (1f - (dist / maxDist)).coerceIn(0f, 1f).pow(2) * 0.8f + 0.1f
        val pan = (dx / (width / 2f)).coerceIn(-1f, 1f)
        
        sonar.updateBeacon(vol, pan)
    }
    
    private fun updateSirenFeedback() {
        val spikePos = maze.getNearestSpike(player.x, player.y)
        if (spikePos != null) {
            val dx = spikePos.x - player.x
            val dy = spikePos.y - player.y
            val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            val maxDist = maze.cellSize * 6f 
            
            if (dist < maxDist) {
                if (maze.hasLineOfSight(player.x, player.y, spikePos.x, spikePos.y)) {
                    val vol = (1f - (dist / maxDist)).coerceIn(0f, 1f).pow(2)
                    val pan = (dx / (width / 2f)).coerceIn(-1f, 1f)
                    sonar.updateSiren(vol, pan)
                } else {
                    sonar.updateSiren(0f, 0f) 
                }
            } else {
                sonar.updateSiren(0f, 0f)
            }
        } else {
            sonar.updateSiren(0f, 0f)
        }
    }
    
    private fun handleVibration() {
        val dist = maze.distanceToWall(player.x, player.y)
        val cellSize = maze.cellSize
        val now = System.currentTimeMillis()
        
        when {
            dist < cellSize * 0.15f -> { 
                 if (!isVibratingContinuous) {
                     vibratePattern(longArrayOf(0, 100), 0) 
                     isVibratingContinuous = true
                 }
            }
            dist < cellSize * 0.6f -> { 
                 stopContinuous()
                 if (now - lastVibrationTime > 150) {
                     vibrateOnce(50)
                     lastVibrationTime = now
                 }
            }
            dist < cellSize * 1.5f -> { 
                 stopContinuous()
                 if (now - lastVibrationTime > 400) {
                     vibrateOnce(50)
                     lastVibrationTime = now
                 }
            }
            dist < cellSize * 4f -> { 
                 stopContinuous()
                 if (now - lastVibrationTime > 1000) {
                     vibrateOnce(50)
                     lastVibrationTime = now
                 }
            }
            else -> stopContinuous()
        }
    }
    
    private fun stopContinuous() {
        if (isVibratingContinuous) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.cancel()
            isVibratingContinuous = false
        }
    }

    private fun draw() {
        if (!holder.surface.isValid) return
        val canvas = holder.lockCanvas() ?: return

        synchronized(maze) {
            canvas.drawColor(Color.BLACK)
            
            canvas.save()
            canvas.translate(-cameraOffsetX, 0f)

            maze.drawDebug(canvas, paint, player.x, player.y, !showMaze)
            player.draw(canvas, paint)
            
            synchronized(networkManager.remotePlayers) {
                paint.color = Color.argb(100, 0, 255, 255) 
                paint.style = Paint.Style.FILL
                for (pos in networkManager.remotePlayers.values) {
                    canvas.drawCircle(pos.x, pos.y, player.radius, paint)
                }
            }
            
            canvas.restore() 
            
            paint.color = Color.argb(50, 255, 255, 255)
            paint.strokeWidth = 5f
            canvas.drawLine(0f, 0f, width.toFloat(), height.toFloat(), paint)
            canvas.drawLine(width.toFloat(), 0f, 0f, height.toFloat(), paint)

            when (gameState) {
                GameState.WAITING -> {
                    drawModernCard(canvas, "Invisible Insight", "Tap to Start")
                    if (highScore != Long.MAX_VALUE) {
                        drawHighScore(canvas, "Best: ${formatTime(highScore)}")
                    }
                }
                GameState.LEVEL_TRANSITION -> {
                    drawModernCard(canvas, "Level Complete!", "Tap for Next Level")
                }
                GameState.GAME_OVER -> drawModernCard(canvas, "Game Over!", "Tap to Restart")
                GameState.WIN -> {
                     if (maze.getCurrentLevel() == 2) { 
                         drawModernCard(canvas, "YOU WIN!", "Time: ${formatTime(elapsedTime)}")
                         drawHighScore(canvas, "Best: ${formatTime(highScore)}")
                     } else {
                         drawModernCard(canvas, "Level Complete!", "")
                     }
                }
                GameState.PLAYING -> {
                    drawTimerBottom(canvas, formatTime(elapsedTime))
                    drawLives(canvas)
                }
            }

            drawSettingsButton(canvas)
            if (!showArrowKeys) {
                drawCalibrateButton(canvas)
            }
        }

        holder.unlockCanvasAndPost(canvas)
    }
    
    private fun drawModernCard(canvas: Canvas, title: String, subtitle: String) {
        val cardWidth = 800f
        val cardHeight = 400f
        val left = (width - cardWidth) / 2f
        val top = (height - cardHeight) / 2f
        val right = left + cardWidth
        val bottom = top + cardHeight
        
        paint.color = Color.argb(100, 0, 0, 0)
        canvas.drawRoundRect(left + 10, top + 10, right + 10, bottom + 10, 40f, 40f, paint)
        
        paint.color = Color.argb(240, 30, 30, 30) 
        canvas.drawRoundRect(left, top, right, bottom, 40f, 40f, paint)
        
        paint.style = Paint.Style.STROKE
        paint.color = Color.GRAY
        paint.strokeWidth = 5f
        canvas.drawRoundRect(left, top, right, bottom, 40f, 40f, paint)
        
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(title, width / 2f, top + 150f, paint)
        
        paint.textSize = 50f
        paint.color = Color.LTGRAY
        canvas.drawText(subtitle, width / 2f, top + 250f, paint)
    }

    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val ms = (millis % 1000) / 10
        return String.format("%d.%02d", seconds, ms)
    }

    private fun drawTextScreen(canvas: Canvas, text: String) {}
    
    private fun drawHighScore(canvas: Canvas, text: String) {
        paint.color = Color.YELLOW
        paint.textSize = 50f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, width / 2f, height / 2f + 280f, paint)
    }

    private fun drawTimerBottom(canvas: Canvas, text: String) {
        val textWidth = paint.measureText(text)
        val centerX = width / 2f - 100f 
        val bgRect = RectF(centerX - 120f, height - 100f, centerX + 120f, height - 20f)
        
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        canvas.drawRect(bgRect, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, centerX, height - 45f, paint)
    }
    
    private fun drawLives(canvas: Canvas) {
        paint.color = Color.RED
        val heartSize = 40f
        val padding = 10f
        val startX = width / 2f + 50f 
        val startY = height - 60f
        
        for (i in 0 until lives) {
            val x = startX + i * (heartSize + padding)
            canvas.drawCircle(x, startY, heartSize / 2, paint)
        }
    }

    private fun drawSettingsButton(canvas: Canvas) {
        paint.color = Color.DKGRAY
        paint.style = Paint.Style.FILL
        canvas.drawRect(settingsButtonRect, paint)
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Settings", settingsButtonRect.centerX(), settingsButtonRect.centerY() + 15, paint)
    }
    
    private fun drawCalibrateButton(canvas: Canvas) {
        paint.color = Color.DKGRAY
        paint.style = Paint.Style.FILL
        canvas.drawRect(calibrateButtonRect, paint)
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Calibrate", calibrateButtonRect.centerX(), calibrateButtonRect.centerY() + 15, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (settingsButtonRect.contains(event.x, event.y)) {
                val intent = Intent(context, SettingsActivity::class.java)
                intent.putExtra("showMaze", showMaze)
                intent.putExtra("showArrowKeys", showArrowKeys)
                intent.putExtra("enableChatbot", enableChatbot) // Pass current setting
                intent.putExtra("controlModeAbsolute", inputController.controlModeAbsolute)
                intent.putExtra("sensitivity", inputController.sensitivity)
                intent.putExtra("vibrationIntensity", vibrationIntensity)
                intent.putExtra("spriteStyle", player.style)
                settingsLauncher.launch(intent)
                return true
            }
            
            if (!showArrowKeys && calibrateButtonRect.contains(event.x, event.y)) {
                inputController.calibrate()
                speak("Calibrated")
                return true
            }
        }

        if (gameState == GameState.PLAYING) {
            if (showArrowKeys) {
                val dx = event.x - player.x 
                val worldTouchX = event.x + cameraOffsetX
                val worldDx = worldTouchX - player.x
                val dy = event.y - player.y
                val dist = sqrt((worldDx*worldDx + dy*dy).toDouble()).toFloat()
                
                val speed = 5f + (inputController.sensitivity / 100f) * 20f
                
                if (dist > 10f) {
                    player.setVelocity((worldDx/dist)*speed, (dy/dist)*speed)
                } else {
                    player.setVelocity(0f, 0f)
                }
            }
        } else if (event.action == MotionEvent.ACTION_DOWN) {
            synchronized(maze) {
                when (gameState) {
                    GameState.WAITING -> {
                        startTime = System.currentTimeMillis()
                        if (!showArrowKeys) inputController.calibrate()
                        speak("Game Started")
                        gameState = GameState.PLAYING
                    }
                    GameState.LEVEL_TRANSITION -> {
                        maze.updateScale(width, height) 
                        player.radius = maze.cellSize / 4f
                        player.reset()
                        cameraOffsetX = 0f
                        startTime = System.currentTimeMillis()
                        if (!showArrowKeys) inputController.calibrate()
                        speak("Next Level Started")
                        gameState = GameState.PLAYING
                    }
                    GameState.GAME_OVER -> {
                        // On reset, we should reload the current level, not necessarily level 1
                        // But the maze class defaults to L1 on loadLevel(1). 
                        // We should probably store the current level in GameView to reset correctly.
                        // For now, sticking to L1 as per previous logic or need fix.
                        maze.loadLevel(maze.getCurrentLevel()) // Reload current level
                        maze.updateScale(width, height)
                        player.radius = maze.cellSize / 4f
                        player.reset()
                        cameraOffsetX = 0f
                        lives = maxLives
                        gameState = GameState.WAITING
                    }
                    GameState.WIN -> {
                        maze.loadLevel(1)
                        maze.updateScale(width, height)
                        player.radius = maze.cellSize / 4f
                        player.reset()
                        cameraOffsetX = 0f
                        lives = maxLives
                        gameState = GameState.WAITING
                    }
                    else -> {}
                }
            }
        }
        return true
    }

    fun resume() {
        running = true
        gameThread = Thread(this)
        gameThread?.start()
        if (!showArrowKeys) inputController.resume()
        networkManager.start()
        sonar.start()
    }

    fun pause() {
        running = false
        try {
            gameThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        inputController.pause()
        networkManager.stop()
        stopContinuous()
        sonar.stop()
        tts?.stop()
    }

    fun loadSettingsAndApply(isTouchMode: Boolean) {
        this.showMaze = prefs.getBoolean("showMaze", false)
        this.showArrowKeys = isTouchMode
        this.enableChatbot = prefs.getBoolean("enableChatbot", true)
        this.inputController.controlModeAbsolute = prefs.getBoolean("controlModeAbsolute", false)
        this.inputController.sensitivity = prefs.getInt("sensitivity", 50)
        this.vibrationIntensity = prefs.getInt("vibrationIntensity", 50)
        this.player.style = prefs.getInt("spriteStyle", 0)
        
        if (running) {
             if (showArrowKeys) inputController.pause() else inputController.resume()
        }
    }

    fun updateSettings(showMaze: Boolean, showArrows: Boolean, absoluteMode: Boolean, sensitivity: Int, vibration: Int, spriteStyle: Int) {
        if (this.showMaze != showMaze) speak(if (showMaze) "Maze visible" else "Maze hidden")
        if (this.showArrowKeys != showArrows) speak(if (showArrows) "Touch mode enabled" else "Gyro mode enabled")
        
        this.showMaze = showMaze
        this.showArrowKeys = showArrows 
        this.inputController.controlModeAbsolute = absoluteMode
        this.inputController.sensitivity = sensitivity
        this.vibrationIntensity = vibration
        this.player.style = spriteStyle
        
        // Reload chatbot setting
        this.enableChatbot = prefs.getBoolean("enableChatbot", true)
        
        if (running) {
            if (showArrowKeys) inputController.pause() else inputController.resume()
        }
    }
    
    fun getControlMode() = inputController.controlModeAbsolute
    fun getSensitivity() = inputController.sensitivity
    fun getVibration() = vibrationIntensity
    fun getSpriteStyle() = player.style

    private fun vibrateOnce(duration: Long) {
         val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
         } else {
             @Suppress("DEPRECATION")
             vibrator.vibrate(duration)
         }
    }
    
    private fun vibratePattern(timings: LongArray, repeat: Int) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, repeat))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, repeat)
        }
    }

    inner class DirectionalSonar {
        private val sampleRate = 44100
        private var audioTrack: AudioTrack? = null
        private var isRunning = false
        private var thread: Thread? = null
        
        @Volatile var isTesting = false

        private val freqs = floatArrayOf(174.61f, 261.63f, 87.31f, 233.08f, 440.0f, 0f)
        val volumes = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
        var beaconPan = 0f 
        var sirenPan = 0f
        var sirenPhase = 0f 

        fun updateBeacon(vol: Float, pan: Float) {
            volumes[4] = vol
            beaconPan = pan
        }
        
        fun updateSiren(vol: Float, pan: Float) {
            volumes[5] = vol
            sirenPan = pan
        }
        
        fun playTestTone(channel: Int, durationMs: Long) {
            isTesting = true
            for(i in volumes.indices) volumes[i] = 0f
            volumes[channel] = 1.0f
            if(channel == 4) beaconPan = 0f
            
            try {
                Thread.sleep(durationMs)
            } catch (e: Exception) {}
            
            volumes[channel] = 0f
            isTesting = false
        }

        fun start() {
            if (isRunning) return
            isRunning = true
            
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, 
                AudioFormat.ENCODING_PCM_16BIT
            )

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO) 
                .build()

            audioTrack = AudioTrack(
                attributes,
                format,
                minBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            
            audioTrack?.play()

            thread = Thread {
                val bufferSize = 1024
                val buffer = ShortArray(bufferSize * 2) 
                var phase = FloatArray(6) 
                var pulsePhase = 0 

                while (isRunning) {
                    for (i in 0 until bufferSize) {
                        var leftSample = 0f
                        var rightSample = 0f
                        
                        val pulsePeriod = 5292 
                        val pulseOn = (pulsePhase % pulsePeriod) < 2646
                        pulsePhase++
                        
                        for (ch in 0 until 6) {
                            var wave = 0f
                            
                            if (ch == 4) { 
                                wave = (sin(phase[ch].toDouble()) * volumes[ch]).toFloat()
                                val lVol = (1f - beaconPan).coerceIn(0f, 1f)
                                val rVol = (1f + beaconPan).coerceIn(0f, 1f)
                                leftSample += wave * lVol * 0.6f
                                rightSample += wave * rVol * 0.6f
                            } else if (ch == 5) { 
                                if (volumes[ch] > 0.01f) {
                                    val freq = 600f + 200f * sin(sirenPhase.toDouble())
                                    sirenPhase += 0.0005f 
                                    val normPhase = (phase[ch] / (2.0 * PI)).toFloat()
                                    val saw = (2.0 * (normPhase - floor(normPhase)) - 1.0).toFloat()
                                    wave = saw * volumes[ch]
                                    
                                    val lVol = (1f - sirenPan).coerceIn(0f, 1f)
                                    val rVol = (1f + sirenPan).coerceIn(0f, 1f)
                                    leftSample += wave * lVol * 0.7f
                                    rightSample += wave * rVol * 0.7f
                                    
                                    phase[ch] += (2.0 * PI * freq / sampleRate).toFloat()
                                    if (phase[ch] > 2.0 * PI) phase[ch] -= (2.0 * PI).toFloat()
                                }
                            } else {
                                if (pulseOn || isTesting) { 
                                    if (ch == 1 || ch == 3) {
                                        val normPhase = (phase[ch] / (2.0 * PI)).toFloat()
                                        val saw = (2.0 * (normPhase - floor(normPhase)) - 1.0).toFloat()
                                        val sine = sin(phase[ch].toDouble()).toFloat()
                                        wave = (0.8f * saw + 0.2f * sine) * volumes[ch]
                                    } else {
                                        wave = (sin(phase[ch].toDouble()) * volumes[ch]).toFloat()
                                    }
                                    
                                    when (ch) {
                                        0, 2 -> { 
                                            leftSample += wave
                                            rightSample += wave
                                        }
                                        1 -> { 
                                            leftSample += wave * 0.2f
                                            rightSample += wave
                                        }
                                        3 -> { 
                                            leftSample += wave
                                            rightSample += wave * 0.2f
                                        }
                                    }
                                }
                            }
                            
                            if (ch != 5) {
                                phase[ch] += (2.0 * PI * freqs[ch] / sampleRate).toFloat()
                                if (phase[ch] > 2.0 * PI) phase[ch] -= (2.0 * PI).toFloat()
                            }
                        }
                        
                        val finalLeft = (leftSample * 0.2f * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        val finalRight = (rightSample * 0.2f * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        
                        buffer[i * 2] = finalLeft.toShort()
                        buffer[i * 2 + 1] = finalRight.toShort()
                    }
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            }
            thread?.start()
        }

        fun stop() {
            isRunning = false
            try {
                thread?.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        }
    }
}

package com.example.invisibleinsight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import java.util.Random

class LightningView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    
    private val path = Path()
    private val random = Random()
    private val handler = Handler(Looper.getMainLooper())
    private var isBoltVisible = false
    private var isRunning = false
    
    // Customization properties
    var minDelay = 100L
    var maxDelay = 1500L
    var boltWidth = 12f
    var glowColor = Color.RED
    var coreColor = Color.parseColor("#FFEEEE")
    var jaggedness = 5f // Higher is more chaotic

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            triggerBolt()
            // Random flicker pattern based on configured speed
            val delay = minDelay + random.nextInt((maxDelay - minDelay).toInt()) 
            handler.postDelayed(this, delay)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRunning = true
        startLightningLoop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun startLightningLoop() {
        handler.removeCallbacks(loopRunnable)
        handler.post(loopRunnable)
    }

    private fun triggerBolt() {
        if (width == 0 || height == 0) return
        
        generateBolt()
        isBoltVisible = true
        invalidate()

        // Flash duration (very short)
        handler.postDelayed({
            isBoltVisible = false
            invalidate()
            
            // Double flash chance
            if (random.nextFloat() > 0.6f && isRunning) {
                handler.postDelayed({
                    isBoltVisible = true
                    generateBolt() // Different shape for second flash
                    invalidate()
                    handler.postDelayed({
                        isBoltVisible = false
                        invalidate()
                    }, 50)
                }, 50)
            }
        }, 80)
    }

    private fun generateBolt() {
        path.reset()
        
        // Start somewhere in the top middle area (or slightly outside for leaking effect)
        var x = width * (0.2f + random.nextFloat() * 0.6f)
        var y = 0f
        path.moveTo(x, y)

        while (y < height) {
            // Jagged movement based on configuration
            val dx = (random.nextFloat() - 0.5f) * (width / jaggedness) 
            val dy = random.nextFloat() * (height / 10f) + 10f
            x += dx
            y += dy
            path.lineTo(x, y)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isBoltVisible) {
            // Outer glow
            paint.color = glowColor
            paint.strokeWidth = boltWidth
            paint.alpha = 150
            paint.setShadowLayer(20f, 0f, 0f, glowColor)
            canvas.drawPath(path, paint)
            paint.clearShadowLayer()

            // Inner core
            paint.color = coreColor
            paint.strokeWidth = boltWidth / 3f
            paint.alpha = 255
            canvas.drawPath(path, paint)
        }
    }
    
    fun configure(minDelay: Long, maxDelay: Long, width: Float, chaos: Float) {
        this.minDelay = minDelay
        this.maxDelay = maxDelay
        this.boltWidth = width
        this.jaggedness = chaos
    }
}

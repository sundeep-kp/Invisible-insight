package com.example.invisibleinsight

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import kotlin.math.cos
import kotlin.math.sin
import java.util.Random

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var modeSelectionLayout: View
    private lateinit var levelSelectionLayout: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_screen)

        prefs = getSharedPreferences("GameProgress", Context.MODE_PRIVATE)
        
        if (!prefs.contains("unlocked_GYRO_1")) {
            prefs.edit().putBoolean("unlocked_GYRO_1", true).apply()
        }

        modeSelectionLayout = findViewById(R.id.mode_selection_layout)
        levelSelectionLayout = findViewById(R.id.level_selection_layout)

        val cardNormalMode: MaterialCardView = findViewById(R.id.card_normal_mode)
        val cardDeathMode: MaterialCardView = findViewById(R.id.card_death_mode)
        val btnBack: View = findViewById(R.id.btn_back)

        cardNormalMode.setOnClickListener {
            showLevelSelection()
        }

        cardDeathMode.setOnClickListener {
            Toast.makeText(this, "Death Mode Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener {
            showModeSelection()
        }
        
        startDeathModeEffects()
        startNormalModeEffects()
    }
    
    private fun startNormalModeEffects() {
        val haloView: View = findViewById(R.id.normal_mode_halo)
        val cardNormalMode: View = findViewById(R.id.card_normal_mode)
        val particlesContainer: FrameLayout = findViewById(R.id.normal_mode_particles)
        
        val haloAnim = ObjectAnimator.ofFloat(haloView, "rotation", 0f, 360f)
        haloAnim.duration = 5000 
        haloAnim.repeatCount = ValueAnimator.INFINITE
        haloAnim.interpolator = LinearInterpolator()
        haloAnim.start()
        
        val scaleXAnim = ObjectAnimator.ofFloat(cardNormalMode, "scaleX", 1.0f, 1.025f, 1.0f)
        scaleXAnim.duration = 3500
        scaleXAnim.repeatCount = ValueAnimator.INFINITE
        scaleXAnim.interpolator = AccelerateDecelerateInterpolator()
        scaleXAnim.start()
        
        val scaleYAnim = ObjectAnimator.ofFloat(cardNormalMode, "scaleY", 1.0f, 1.025f, 1.0f)
        scaleYAnim.duration = 3500
        scaleYAnim.repeatCount = ValueAnimator.INFINITE
        scaleYAnim.interpolator = AccelerateDecelerateInterpolator()
        scaleYAnim.start()
        
        val particleRunnable = object : Runnable {
            override fun run() {
                if (particlesContainer.width > 0) {
                    spawnParticle(particlesContainer)
                }
                handler.postDelayed(this, 400) 
            }
        }
        handler.post(particleRunnable)
    }
    
    private fun spawnParticle(container: FrameLayout) {
        val particle = ImageView(this)
        particle.setImageResource(R.drawable.particle_green)
        
        val sizeDp = 2 + random.nextFloat() * 2
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val params = FrameLayout.LayoutParams(sizePx, sizePx)
        
        val startX = random.nextFloat() * container.width
        
        params.leftMargin = 0
        params.topMargin = 0
        particle.translationX = startX
        particle.translationY = container.height.toFloat()
        
        particle.alpha = 0.1f + random.nextFloat() * 0.3f 
        container.addView(particle, params)
        
        val duration = 4000 + random.nextInt(3000).toLong() 
        val upAnim = ObjectAnimator.ofFloat(particle, "translationY", container.height.toFloat(), -50f)
        upAnim.duration = duration
        upAnim.interpolator = LinearInterpolator()
        
        upAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                container.removeView(particle)
            }
        })
        upAnim.start()
    }
    
    private fun startDeathModeEffects() {
        val glowView: View = findViewById(R.id.death_mode_glow)
        val sparksContainer: FrameLayout = findViewById(R.id.sparks_container)
        val cardDeathMode: View = findViewById(R.id.card_death_mode)
        
        val lightning1 = findViewById<LightningView>(R.id.lightning_layer_1)
        val lightning2 = findViewById<LightningView>(R.id.lightning_layer_2)
        val lightning3 = findViewById<LightningView>(R.id.lightning_layer_3)

        lightning1.configure(minDelay = 800, maxDelay = 2500, width = 16f, chaos = 3f)
        lightning2.configure(minDelay = 300, maxDelay = 1200, width = 10f, chaos = 5f)
        lightning3.configure(minDelay = 50, maxDelay = 400, width = 5f, chaos = 10f)

        val glowAnim = ObjectAnimator.ofFloat(glowView, "alpha", 0.9f, 1f, 0.9f)
        glowAnim.duration = 150
        glowAnim.repeatCount = ValueAnimator.INFINITE
        glowAnim.interpolator = LinearInterpolator()
        glowAnim.start()
        
        val jitterRunnable = object : Runnable {
            override fun run() {
                val dx = (random.nextFloat() - 0.5f) * 4f 
                val dy = (random.nextFloat() - 0.5f) * 4f
                cardDeathMode.translationX = dx
                cardDeathMode.translationY = dy
                handler.postDelayed(this, 50)
            }
        }
        handler.post(jitterRunnable)
        
        val centerX = 168f * resources.displayMetrics.density 
        val centerY = 75f * resources.displayMetrics.density 
        val radiusX = 168f * resources.displayMetrics.density
        val radiusY = 75f * resources.displayMetrics.density
        
        for (i in 0 until 6) {
            val spark = ImageView(this)
            spark.setImageResource(R.drawable.spark)
            val size = 8f * resources.displayMetrics.density
            val params = FrameLayout.LayoutParams(size.toInt(), size.toInt())
            sparksContainer.addView(spark, params)
            
            val angleOffset = (i * 60).toFloat()
            
            val sparkAnim = ValueAnimator.ofFloat(0f, 360f)
            sparkAnim.duration = 900
            sparkAnim.repeatCount = ValueAnimator.INFINITE
            sparkAnim.interpolator = LinearInterpolator()
            sparkAnim.addUpdateListener { animation ->
                val angle = (animation.animatedValue as Float) + angleOffset
                val rad = Math.toRadians(angle.toDouble())
                val x = centerX + (radiusX * cos(rad)).toFloat()
                val y = centerY + (radiusY * sin(rad)).toFloat()
                spark.x = x
                spark.y = y
                
                if (i % 2 == 0) spark.setColorFilter(Color.RED) else spark.setColorFilter(Color.WHITE)
            }
            sparkAnim.start()
        }
    }
    
    private fun showModeSelection() {
        modeSelectionLayout.visibility = View.VISIBLE
        levelSelectionLayout.visibility = View.GONE
    }

    private fun showLevelSelection() {
        modeSelectionLayout.visibility = View.GONE
        levelSelectionLayout.visibility = View.VISIBLE
        
        setupSeries("GYRO", listOf(R.id.gyro_l1, R.id.gyro_l2, R.id.gyro_l3))
        setupSeries("TOUCH", listOf(R.id.touch_l1, R.id.touch_l2, R.id.touch_l3))
        setupSeries("ABSOLUTE", listOf(R.id.absolute_l1, R.id.absolute_l2, R.id.absolute_l3))
    }

    private fun setupSeries(mode: String, cardIds: List<Int>) {
        for (i in cardIds.indices) {
            val level = i + 1
            val cardView = findViewById<MaterialCardView>(cardIds[i])
            setupLevelCard(cardView, mode, level)
        }
    }

    private fun setupLevelCard(card: MaterialCardView, mode: String, level: Int) {
        val isUnlocked = prefs.getBoolean("unlocked_${mode}_$level", false)
        val iconView = card.findViewById<ImageView>(R.id.level_icon)
        val numberView = card.findViewById<TextView>(R.id.level_number)
        val bestTimeView = card.findViewById<TextView>(R.id.level_best_time)
        val mapPreview = card.findViewById<MapPreviewView>(R.id.map_preview)
        val container = card.getChildAt(0) as FrameLayout // Updated to FrameLayout

        numberView.text = "Level $level"

        if (isUnlocked) {
            card.alpha = 1.0f 
            // Show Map Preview
            mapPreview.visibility = View.VISIBLE
            mapPreview.setLevel(level)
            
            // Hide Lock Icon or overlay? Let's hide icon to show map clearly
            iconView.visibility = View.GONE
            
            numberView.setTextColor(Color.WHITE)
            numberView.alpha = 1.0f
            // Background color managed by layout XML usually, but we can set it
            // container.setBackgroundColor(Color.parseColor("#424242")) 
            // Since container is FrameLayout, maybe better to set background on card
            card.setCardBackgroundColor(Color.parseColor("#424242"))
            
            val bestTime = prefs.getLong("best_time_${mode}_$level", -1L)
            if (bestTime != -1L) {
                bestTimeView.visibility = View.VISIBLE
                bestTimeView.text = formatTime(bestTime)
            } else {
                bestTimeView.visibility = View.GONE
            }
            
            card.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("initialMode", mode)
                intent.putExtra("initialLevel", level)
                startActivity(intent)
            }
        } else {
            card.alpha = 1.0f
            mapPreview.visibility = View.GONE
            iconView.visibility = View.VISIBLE
            iconView.setImageResource(android.R.drawable.ic_lock_lock)
            iconView.setColorFilter(Color.LTGRAY)
            iconView.alpha = 1.0f
            
            numberView.setTextColor(Color.GRAY)
            numberView.alpha = 0.5f
            card.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
            
            bestTimeView.visibility = View.GONE
            
            card.setOnClickListener {
                Toast.makeText(this, "Complete previous levels to unlock!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val ms = (millis % 1000) / 10
        return String.format("%d.%02d s", seconds, ms)
    }
    
    override fun onResume() {
        super.onResume()
        if (levelSelectionLayout.visibility == View.VISIBLE) {
            showLevelSelection()
        }
    }
}

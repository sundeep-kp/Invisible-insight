package com.example.invisibleinsight

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sqrt

class Player(private val maze: Maze) {

    var x: Float = 0f
    var y: Float = 0f
    var radius: Float = 20f 
    var style: Int = 0 // 0: Cyan Circle, 1: Pink Square, 2: Gold Triangle, 3: Ghost

    var vx: Float = 0f
    var vy: Float = 0f

    companion object {
        private const val MAX_SPEED = 10f
    }

    init {
        reset()
    }

    fun update() {
        radius = maze.cellSize / 4f
        x += vx
        y += vy
    }

    fun draw(canvas: Canvas, paint: Paint) {
        when (style) {
            0 -> { // Cyan Circle
                paint.color = Color.CYAN
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, radius, paint)
            }
            1 -> { // Neon Pink Square
                paint.color = Color.parseColor("#FF00FF")
                paint.style = Paint.Style.FILL
                canvas.drawRect(x - radius, y - radius, x + radius, y + radius, paint)
            }
            2 -> { // Gold Triangle
                paint.color = Color.parseColor("#FFD700")
                paint.style = Paint.Style.FILL
                val path = Path()
                path.moveTo(x, y - radius) // Top
                path.lineTo(x + radius, y + radius) // Bottom Right
                path.lineTo(x - radius, y + radius) // Bottom Left
                path.close()
                canvas.drawPath(path, paint)
            }
            3 -> { // Ghost (Translucent White)
                paint.color = Color.WHITE
                paint.alpha = 150
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, radius, paint)
                // Reset alpha for other draws
                paint.alpha = 255 
            }
            else -> { // Default
                paint.color = Color.CYAN
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, radius, paint)
            }
        }
    }

    fun setVelocity(dx: Float, dy: Float) {
        val speed = sqrt(dx * dx + dy * dy)
        if (speed > MAX_SPEED) {
            vx = (dx / speed) * MAX_SPEED
            vy = (dy / speed) * MAX_SPEED
        } else {
            vx = dx
            vy = dy
        }
    }

    fun reset() {
        val startPos = maze.getStart()
        x = startPos.x
        y = startPos.y
        vx = 0f
        vy = 0f
    }
}

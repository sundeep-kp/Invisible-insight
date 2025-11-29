package com.example.invisibleinsight

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.max

class Maze {

    // Dynamic cell size
    var cellSize = 75f
    private var currentLevel = 1
    private var grid: Array<IntArray> = emptyArray()
    
    // Map dimensions in pixels
    var mapWidth = 0f
    var mapHeight = 0f

    // Level 1: A big fat straight line
    private val level1 = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 1), // Goal 2x2
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
    )

    // Level 2: More complex, narrower paths
    private val level2 = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1),
        intArrayOf(1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1),
        intArrayOf(1, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 2),
        intArrayOf(1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 2, 2),
        intArrayOf(1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
    )
    
    // Level 3: Scrolling Map
    // 0=Path, 1=Wall, 2=Goal, 3=Checkpoint, 4=Spike Obstacle
    private val level3 = arrayOf(
        // A very wide map (40 columns)
        intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
        intArrayOf(1,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1),
        intArrayOf(1,0,1,1,1,1,1,0,1,0,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,0,1),
        intArrayOf(1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1),
        intArrayOf(1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1),
        intArrayOf(1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1), // Spikes (4)
        intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1),
        intArrayOf(1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,0,4,0,0,0,0,0,0,0,0,0,2,1), // Goal
        intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1)
    )

    init {
        loadLevel(1)
    }

    fun loadLevel(level: Int) {
        currentLevel = level
        grid = when(level) {
            1 -> level1
            2 -> level2
            3 -> level3
            else -> level1
        }
        updateMapDimensions()
    }
    
    private fun updateMapDimensions() {
        if (grid.isNotEmpty()) {
            mapWidth = grid[0].size * cellSize
            mapHeight = grid.size * cellSize
        }
    }

    fun nextLevel(): Boolean {
        if (currentLevel < 3) {
            loadLevel(currentLevel + 1)
            return true
        }
        return false
    }
    
    fun getCurrentLevel(): Int {
        return currentLevel
    }

    fun updateScale(screenWidth: Int, screenHeight: Int) {
        if (grid.isEmpty()) return
        val cols = grid[0].size
        val rows = grid.size
        
        if (currentLevel == 3) {
            // Scrolling level: Cell size is fixed relative to screen width (e.g., 15 cols visible)
            // This ensures the map extends beyond the screen
            cellSize = screenWidth.toFloat() / 15f
        } else {
            // Static levels: Fit to screen
            val cellW = screenWidth.toFloat() / cols
            val cellH = screenHeight.toFloat() / rows
            cellSize = min(cellW, cellH)
        }
        updateMapDimensions()
    }

    fun getStart(): PointF {
        return PointF(1.5f * cellSize, 1.5f * cellSize) // Default start
    }
    
    fun getNearestCheckpoint(playerX: Float, playerY: Float): PointF {
        var nearest = getStart()
        var minDst = Float.MAX_VALUE
        
        val start = getStart()
        val startDst = hypot((playerX-start.x).toDouble(), (playerY-start.y).toDouble()).toFloat()
        minDst = startDst
        nearest = start

        for (y in grid.indices) {
            for (x in grid[y].indices) {
                if (grid[y][x] == 3) {
                    val cpX = x * cellSize + cellSize/2f
                    val cpY = y * cellSize + cellSize/2f
                    val dist = hypot((playerX-cpX).toDouble(), (playerY-cpY).toDouble()).toFloat()
                    
                    if (dist < minDst) {
                        minDst = dist
                        nearest = PointF(cpX, cpY)
                    }
                }
            }
        }
        return nearest
    }
    
    fun getWinPos(): PointF {
        var sumX = 0f
        var sumY = 0f
        var count = 0
        for (y in grid.indices) {
            for (x in grid[y].indices) {
                if (grid[y][x] == 2) {
                    sumX += x * cellSize + cellSize / 2f
                    sumY += y * cellSize + cellSize / 2f
                    count++
                }
            }
        }
        return if (count > 0) PointF(sumX / count, sumY / count) else PointF(0f, 0f)
    }
    
    fun getNearestSpike(playerX: Float, playerY: Float): PointF? {
        var nearest: PointF? = null
        var minDst = Float.MAX_VALUE
        
        for (y in grid.indices) {
            for (x in grid[y].indices) {
                if (grid[y][x] == 4) {
                    val spX = x * cellSize + cellSize/2f
                    val spY = y * cellSize + cellSize/2f
                    val dist = hypot((playerX-spX).toDouble(), (playerY-spY).toDouble()).toFloat()
                    
                    if (dist < minDst) {
                        minDst = dist
                        nearest = PointF(spX, spY)
                    }
                }
            }
        }
        return nearest
    }

    fun isWall(px: Float, py: Float): Boolean {
        val x = (px / cellSize).toInt()
        val y = (py / cellSize).toInt()
        if (y < 0 || y >= grid.size || x < 0 || x >= grid[0].size) return true
        val cell = grid[y][x]
        return cell == 1 || cell == 4 // 1 is Wall, 4 is Spike (also solid/dangerous)
    }
    
    fun isSpike(px: Float, py: Float): Boolean {
        val x = (px / cellSize).toInt()
        val y = (py / cellSize).toInt()
        if (y < 0 || y >= grid.size || x < 0 || x >= grid[0].size) return false
        return grid[y][x] == 4
    }

    fun isWin(px: Float, py: Float): Boolean {
        val x = (px / cellSize).toInt()
        val y = (py / cellSize).toInt()
        if (y < 0 || y >= grid.size || x < 0 || x >= grid[0].size) return false
        return grid[y][x] == 2
    }

    fun distanceToWall(px: Float, py: Float): Float {
        if (isWall(px, py)) return 0f

        var minDistance = Float.MAX_VALUE
        val searchRadius = 2 

        val playerGridX = (px / cellSize).toInt()
        val playerGridY = (py / cellSize).toInt()

        for (y in (playerGridY - searchRadius)..(playerGridY + searchRadius)) {
            for (x in (playerGridX - searchRadius)..(playerGridX + searchRadius)) {
                if (y < 0 || y >= grid.size || x < 0 || x >= grid[0].size) continue
                val cell = grid[y][x]
                if (cell != 1 && cell != 4) continue

                val wallRect = RectF(x * cellSize, y * cellSize, (x + 1) * cellSize, (y + 1) * cellSize)

                val dx = px - wallRect.centerX()
                val dy = py - wallRect.centerY()
                val distToCenter = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val distToEdge = max(0f, distToCenter - cellSize / 2f)

                if (distToEdge < minDistance) {
                    minDistance = distToEdge
                }
            }
        }
        return minDistance
    }
    
    // Check if there is a straight line line-of-sight between two points
    // Returns true if clear, false if blocked by wall
    fun hasLineOfSight(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val dist = hypot((endX - startX).toDouble(), (endY - startY).toDouble()).toFloat()
        val steps = (dist / (cellSize / 4)).toInt() // Check every quarter cell
        
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val checkX = startX + (endX - startX) * t
            val checkY = startY + (endY - startY) * t
            if (isWall(checkX, checkY)) {
                return false
            }
        }
        return true
    }
    
    fun raycast(startX: Float, startY: Float, dirX: Float, dirY: Float, maxDist: Float): Float {
        var currentX = startX
        var currentY = startY
        var dist = 0f
        val step = cellSize / 4f 

        while (dist < maxDist) {
            if (isWall(currentX, currentY)) {
                return dist
            }
            currentX += dirX * step
            currentY += dirY * step
            dist += step
        }
        return maxDist
    }


    fun drawDebug(canvas: Canvas, paint: Paint, playerX: Float, playerY: Float, spotlight: Boolean) {
        if (spotlight) {
            val radius = 3.5f * cellSize
            for (y in grid.indices) {
                for (x in grid[y].indices) {
                    val rect = RectF(x * cellSize, y * cellSize, (x + 1) * cellSize, (y + 1) * cellSize)
                    val dx = playerX - rect.centerX()
                    val dy = playerY - rect.centerY()
                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                    if (dist < radius) {
                        val alpha = (1 - dist / radius) * 255
                        drawCell(canvas, paint, rect, grid[y][x], alpha.toInt())
                    }
                }
            }
        } else {
            for (y in grid.indices) {
                for (x in grid[y].indices) {
                    val rect = RectF(x * cellSize, y * cellSize, (x + 1) * cellSize, (y + 1) * cellSize)
                    drawCell(canvas, paint, rect, grid[y][x], 255)
                }
            }
        }
    }

    private fun drawCell(canvas: Canvas, paint: Paint, rect: RectF, cellType: Int, alpha: Int) {
        when (cellType) {
            1 -> { // Wall
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.alpha = alpha
                canvas.drawRect(rect, paint)
            }
            2 -> { // Win
                paint.style = Paint.Style.STROKE
                paint.color = Color.GREEN
                paint.strokeWidth = 5f
                paint.alpha = alpha
                canvas.drawRect(rect, paint)
            }
            3 -> { // Checkpoint
                paint.style = Paint.Style.FILL
                paint.color = Color.DKGRAY
                paint.alpha = alpha / 2
                canvas.drawRect(rect, paint)
            }
            4 -> { // Spikes
                paint.style = Paint.Style.STROKE
                paint.color = Color.RED
                paint.strokeWidth = 3f
                paint.alpha = alpha
                
                // Draw X shape for spike
                canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, paint)
                canvas.drawLine(rect.right, rect.top, rect.left, rect.bottom, paint)
                canvas.drawRect(rect, paint)
            }
        }
    }
}

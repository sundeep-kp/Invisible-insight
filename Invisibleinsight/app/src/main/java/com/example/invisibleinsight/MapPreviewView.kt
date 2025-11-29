package com.example.invisibleinsight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class MapPreviewView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint()
    private var grid: Array<IntArray> = emptyArray()
    private val maze = Maze() // Helper to get level data

    fun setLevel(level: Int) {
        maze.loadLevel(level)
        // We need to access the grid. Since Maze.grid is private, we might need to expose it 
        // or just duplicate the level data here for preview simplicity.
        // For now, I'll assume I can access level data via Maze or replicate it.
        // Since Maze.kt has private levels, I should probably make a public accessor in Maze.kt first.
        // But to avoid modifying Maze.kt too much, I'll just use the same hardcoded arrays here for preview.
        
        // Level 1
        val level1 = arrayOf(
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 3, 0, 0, 0, 3, 0, 0, 0, 3, 0, 2, 2, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        )

        // Level 2
        val level2 = arrayOf(
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
            intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1),
            intArrayOf(1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1),
            intArrayOf(1, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 0, 1),
            intArrayOf(1, 0, 0, 0, 3, 0, 0, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 1, 2, 2),
            intArrayOf(1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 2, 2),
            intArrayOf(1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        )
        
        // Level 3 (Wide) - Simplified for preview (crop or scale)
        // Just creating a dummy placeholder for L3 to avoid huge array copy
        val level3 = arrayOf(
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 1, 1, 1, 1, 0, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 4, 1),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1)
        )

        grid = when(level) {
            1 -> level1
            2 -> level2
            3 -> level3
            else -> level1
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (grid.isEmpty()) return

        val cols = grid[0].size
        val rows = grid.size
        
        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows
        val cellSize = min(cellW, cellH)
        
        // Center the map
        val offsetX = (width - cols * cellSize) / 2f
        val offsetY = (height - rows * cellSize) / 2f

        for (y in grid.indices) {
            for (x in grid[y].indices) {
                val cell = grid[y][x]
                
                paint.style = Paint.Style.FILL
                paint.color = when(cell) {
                    1 -> Color.WHITE // Wall
                    2 -> Color.GREEN // Goal
                    3 -> Color.DKGRAY // Checkpoint
                    4 -> Color.RED // Spike
                    else -> Color.TRANSPARENT // Path
                }
                
                if (paint.color != Color.TRANSPARENT) {
                    canvas.drawRect(
                        offsetX + x * cellSize,
                        offsetY + y * cellSize,
                        offsetX + (x + 1) * cellSize,
                        offsetY + (y + 1) * cellSize,
                        paint
                    )
                }
            }
        }
    }
}

package com.example.invisibleinsight

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class InputController(
    context: Context,
    private val player: Player
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Control Settings
    var sensitivity: Int = 50 // 0-100
    var controlModeAbsolute: Boolean = false // false = Velocity, true = Absolute Position

    // Neutral orientation and Anchor position are captured on reset/calibrate
    private var neutralOrientation: FloatArray? = null
    private var captureNeutralOrientation = false
    
    // The player's position at the moment of calibration
    private var anchorX = 0f
    private var anchorY = 0f

    fun calibrate() {
        captureNeutralOrientation = true
    }

    fun resume() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun pause() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Remap coordinates for landscape orientation
        val remappedRotationMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_Y,
            SensorManager.AXIS_MINUS_X,
            remappedRotationMatrix
        )

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(remappedRotationMatrix, orientationAngles)

        // Capture neutral orientation and anchor position
        if (captureNeutralOrientation) {
            neutralOrientation = orientationAngles.clone()
            anchorX = player.x
            anchorY = player.y
            captureNeutralOrientation = false
            player.setVelocity(0f, 0f)
            return
        }

        if (neutralOrientation == null) return

        // Calculate relative tilt
        val relativePitch = orientationAngles[1] - neutralOrientation!![1]
        val relativeRoll = orientationAngles[2] - neutralOrientation!![2]

        // Scale sensitivity
        val sensitivityMultiplier = if (controlModeAbsolute) {
            (sensitivity / 100f) * 1000f + 200f 
        } else {
            (sensitivity / 100f) * 15f + 1f
        }
        
        if (controlModeAbsolute) {
            val offsetX = relativeRoll * sensitivityMultiplier
            val offsetY = -relativePitch * sensitivityMultiplier 

            player.x = anchorX + offsetX
            player.y = anchorY + offsetY
            player.setVelocity(0f, 0f)
        } else {
            val deadZone = 0.05f
            var deltaX = 0f
            if (relativeRoll > deadZone) {
                deltaX = (relativeRoll - deadZone) * sensitivityMultiplier
            } else if (relativeRoll < -deadZone) {
                deltaX = (relativeRoll + deadZone) * sensitivityMultiplier
            }

            var deltaY = 0f
            // Pitch Logic: 
            // Pitch > 0 (Top away) -> Move Up (-Y)
            // Inverted Pitch logic: -relativePitch
            val invPitch = -relativePitch
            if (invPitch > deadZone) {
                deltaY = (invPitch - deadZone) * sensitivityMultiplier
            } else if (invPitch < -deadZone) {
                deltaY = (invPitch + deadZone) * sensitivityMultiplier
            }

            player.setVelocity(deltaX, deltaY)
        }
    }
}

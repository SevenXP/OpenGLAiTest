package com.example.myapplication.dice

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shake detector using low-pass and high-pass filtering.
 * Detects phone shaking and reports intensity.
 */
class ShakeDetector(
    private val context: Context,
    private val onShakeStart: (inertia: Float) -> Unit,
    private val onShakeEnd: () -> Unit
) : SensorEventListener {

    // ==================== STATE ====================

    /** Current acceleration magnitude */
    private var currentAccel = 0.0f

    /** Previous acceleration magnitude */
    private var previousAccel = 0.0f

    /** Current gravity estimate */
    private var currentGravity = 0.0f

    /** Previous gravity estimate */
    private var previousGravity = 0.0f

    /** Peak acceleration during shake */
    private var peakAccel = 0.0f

    /** Shake state */
    private var isShaking = false

    /** Shake start time */
    private var shakeStartTime = 0L

    /** Shake duration */
    private var shakeDuration = 0L

    /** Quiet samples counter */
    private var quietSamples = 0

    /** Sensor manager */
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** Accelerometer sensor */
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Coroutine scope for callbacks */
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    /** Job for coroutine cancellation */
    private var job: Job? = null

    /** Is sensor enabled */
    private var isSensorEnabled = false

    // ==================== INITIALIZATION ====================

    init {
        // Check if accelerometer is available
        if (accelerometer != null) {
            isSensorEnabled = true
        } else {
            // Log warning if accelerometer not available
            android.util.Log.w("ShakeDetector", "Accelerometer not available - shake detection disabled")
        }
    }

    // ==================== SENSOR LISTENER ====================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER || !isSensorEnabled) {
            return
        }

        val values = event.values
        val x = values[0].toDouble()
        val y = values[1].toDouble()
        val z = values[2].toDouble()

        // Calculate magnitude
        val accel = Math.sqrt(x * x + y * y + z * z).toFloat()

        // Low-pass filter for gravity estimation
        currentGravity = DiceConfig.SHAKE_LP_ALPHA * currentGravity + (1 - DiceConfig.SHAKE_LP_ALPHA) * accel

        // High-pass filter for movement
        val accelNoGravity = accel - currentGravity

        // Update current acceleration
        currentAccel = accelNoGravity

        // Check for shake start
        if (!isShaking && currentAccel > DiceConfig.SHAKE_THRESH) {
            isShaking = true
            peakAccel = currentAccel
            shakeStartTime = System.currentTimeMillis()
            quietSamples = 0

            // Calculate inertia
            val inertia = Math.min(peakAccel / DiceConfig.SHAKE_INERTIA_DIVISOR, DiceConfig.SHAKE_INERTIA_MAX)

            // Report shake start on main thread
            scope.launch {
                onShakeStart(inertia)
            }
        }

        // Check for shake end
        if (isShaking) {
            // Update peak acceleration
            if (currentAccel > peakAccel) {
                peakAccel = currentAccel
            }

            // Check if acceleration has dropped below quiet threshold
            if (currentAccel < DiceConfig.SHAKE_QUIET_THRESH) {
                quietSamples++

                // Check if we've had enough quiet samples
                if (quietSamples >= DiceConfig.SHAKE_QUIET_NEEDED) {
                    isShaking = false
                    shakeDuration = System.currentTimeMillis() - shakeStartTime

                    // Report shake end on main thread
                    scope.launch {
                        onShakeEnd()
                    }
                }
            } else {
                // Reset quiet counter if acceleration is still high
                quietSamples = 0
            }
        }

        // Update previous values
        previousAccel = currentAccel
        previousGravity = currentGravity
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ignore accuracy changes
    }

    // ==================== PUBLIC METHODS ====================

    /**
     * Start listening for shakes
     */
    fun start() {
        if (isSensorEnabled && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /**
     * Stop listening for shakes
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Check if shake detection is enabled
     */
    fun isEnabled(): Boolean {
        return isSensorEnabled
    }

    /**
     * Get current shake state
     */
    fun isShaking(): Boolean {
        return isShaking
    }

    /**
     * Get peak acceleration during current shake
     */
    fun getPeakAccel(): Float {
        return peakAccel
    }

    /**
     * Get shake duration
     */
    fun getShakeDuration(): Long {
        return shakeDuration
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stop()
    }
}
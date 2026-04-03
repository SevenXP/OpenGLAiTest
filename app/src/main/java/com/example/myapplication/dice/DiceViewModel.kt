package com.example.myapplication.dice

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the dice game.
 * Manages game state, roll/stop actions, and auto-stop timer.
 */
class DiceViewModel(context: Context) : ViewModel() {

    // ==================== STATE ====================

    /** Current result (null during rolling) */
    private val _result = MutableStateFlow<Pair<Int, Int>?>(null)
    val result: StateFlow<Pair<Int, Int>?> = _result.asStateFlow()

    /** Whether dice are currently rolling */
    private val _rolling = MutableStateFlow(false)
    val rolling: StateFlow<Boolean> = _rolling.asStateFlow()

    /** Stop progress (0.0 to 1.0) */
    private val _stopProgress = MutableStateFlow(0.0f)
    val stopProgress: StateFlow<Float> = _stopProgress.asStateFlow()

    /** Whether to show hint */
    private val _showHint = MutableStateFlow(true)
    val showHint: StateFlow<Boolean> = _showHint.asStateFlow()

    /** Current state */
    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    // ==================== VIBRATOR ====================

    /** Vibrator for haptic feedback */
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // ==================== AUTO-STOP TIMER ====================

    /** Handler for auto-stop timer */
    private val handler = Handler(Looper.getMainLooper())

    /** Runnable for auto-stop */
    private val autoStopRunnable = Runnable {
        stop(1f)
    }

    // ==================== INITIALIZATION ====================

    init {
        // Check if vibrator is available
        if (!vibrator.hasVibrator()) {
            Log.w("DiceViewModel", "Vibrator not available")
        }
    }

    // ==================== PUBLIC METHODS ====================

    /**
     * Start rolling the dice
     */
    fun roll() {
        // Reset state
        _result.value = null
        _rolling.value = true
        _stopProgress.value = 0.0f
        _showHint.value = false
        _state.value = State.ROLLING

        // Cancel any existing auto-stop timer
        handler.removeCallbacks(autoStopRunnable)

        // Start auto-stop timer
        handler.postDelayed(autoStopRunnable, DiceConfig.AUTO_STOP_MS)

        // Trigger haptic feedback
        vibrate(DiceConfig.HAPTIC_ROLL_AMPLITUDE)
    }

    /**
     * Stop rolling the dice with specified inertia
     */
    fun stop(inertia: Float = 1f) {
        // Cancel auto-stop timer
        handler.removeCallbacks(autoStopRunnable)

        // Update state
        _rolling.value = false
        _state.value = State.STOPPING

        // Trigger haptic feedback
        vibrate(DiceConfig.HAPTIC_RESULT_AMPLITUDE)

        // Wait for animation to complete
        viewModelScope.launch {
            delay(500) // Wait for snap animation

            // Update result
            val renderer = DiceRenderer()
            val die1Result = renderer.getDie1Result()
            val die2Result = renderer.getDie2Result()
            _result.value = Pair(die1Result, die2Result)

            // Update state
            _state.value = State.FINISHED
        }
    }

    /**
     * Reset the dice
     */
    fun reset() {
        // Cancel auto-stop timer
        handler.removeCallbacks(autoStopRunnable)

        // Reset state
        _result.value = null
        _rolling.value = false
        _stopProgress.value = 0.0f
        _showHint.value = true
        _state.value = State.IDLE
    }

    /**
     * Check if dice are currently rolling
     */
    fun isRolling(): Boolean {
        return _rolling.value
    }

    /**
     * Check if dice are currently stopping
     */
    fun isStopping(): Boolean {
        return _state.value == State.STOPPING
    }

    /**
     * Check if dice are finished
     */
    fun isFinished(): Boolean {
        return _state.value == State.FINISHED
    }

    /**
     * Get current state
     */
    fun getState(): State {
        return _state.value
    }

    /**
     * Vibrate with specified amplitude
     */
    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(amplitude: Int) {
        if (vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(amplitude.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(amplitude.toLong())
                }
            } catch (e: Exception) {
                Log.e("DiceViewModel", "Vibration failed", e)
            }
        }
    }

    // ==================== STATE ENUM ====================

    enum class State {
        IDLE,        // Waiting for roll
        ROLLING,     // Dice are rolling
        STOPPING,    // Dice are stopping
        FINISHED     // Animation complete
    }

    // ==================== CLEANUP ====================

    override fun onCleared() {
        super.onCleared()
        // Cancel auto-stop timer
        handler.removeCallbacks(autoStopRunnable)
    }
}
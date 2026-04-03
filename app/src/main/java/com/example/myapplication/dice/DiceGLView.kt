package com.example.myapplication.dice

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.View
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView wrapper for the dice game.
 * Handles touch events for shake detection.
 */
class DiceGLView(context: Context) : GLSurfaceView(context) {
    
    /** Renderer for the dice game */
    private val renderer: DiceRenderer
    
    /** Touch start time */
    private var touchStartTime = 0L
    
    /** Touch start coordinates */
    private var touchStartX = 0f
    private var touchStartY = 0f
    
    init {
        // Set render context
        setEGLContextClientVersion(2)
        
        // Set renderer
        renderer = DiceRenderer()
        setRenderer(renderer)
        
        // Set render mode
        setRenderMode(RENDERMODE_WHEN_DIRTY)
        
        // Enable touch events
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartTime = System.currentTimeMillis()
                    touchStartX = event.x
                    touchStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val touchDuration = System.currentTimeMillis() - touchStartTime
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY
                    
                    // Check if this is a shake gesture
                    if (touchDuration > 100 && touchDuration < 500) {
                        // Calculate gesture magnitude
                        val gestureMagnitude = Math.sqrt(deltaX.toDouble() * deltaX.toDouble() + deltaY.toDouble() * deltaY.toDouble()).toFloat()
                        
                        // If gesture is significant, trigger shake
                        if (gestureMagnitude > 50) {
                            // Trigger shake
                            triggerShake(gestureMagnitude)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Trigger shake with specified magnitude
     */
    private fun triggerShake(magnitude: Float) {
        // Request render
        requestRender()
        
        // Notify renderer of shake
        // Note: In a full implementation, this would be handled through a callback
        // For now, we'll rely on the ViewModel to handle shake detection
    }
    
    /**
     * Get the renderer
     */
    fun getRenderer(): DiceRenderer {
        return renderer
    }
    
    /**
     * Request a render
     */
    override fun requestRender() {
        super.requestRender()
    }
}
package com.example.myapplication

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.opengl.CubeRenderer

/**
 * MainActivity demonstrates how to use CubeRenderer with 60 FPS limit.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var glSurfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create GLSurfaceView instance
        glSurfaceView = GLSurfaceView(this).apply {
            // Set OpenGL ES 2.0 version
            setEGLContextClientVersion(2)
            
            // Set the renderer
            setRenderer(CubeRenderer(this@MainActivity))
            
            // IMPORTANT: Configure for smooth 60 FPS rendering
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        
        setContentView(glSurfaceView)
    }

    override fun onResume() {
        super.onResume()
        // Resume the GL thread when activity resumes
        glSurfaceView.onResume()
    }

    override fun onPause() {
        // Pause the GL thread when activity pauses
        glSurfaceView.onPause()
        super.onPause()
    }
}
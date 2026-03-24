package com.example.myapplication.opengl

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Choreographer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentOpenGlBinding
import com.example.myapplication.opengl.OpenGLFragment.Companion.MAX_FPS

class OpenGLFragment : Fragment() {
    // Nullable private binding to prevent memory leaks
    private var _binding: FragmentOpenGlBinding? = null

    // Non-null binding property for convenient access
    private val binding get() = _binding!!
    private lateinit var cubeRenderer: GLSurfaceView.Renderer

    /** When true, GL draws only on requestRender(), throttled to [MAX_FPS]. */
    private var renderThrottleActive = false

    private val choreographer: Choreographer get() = Choreographer.getInstance()

    private val throttledFrameRequest = object : Choreographer.FrameCallback {
        private var lastRenderTimeNs = 0L

        override fun doFrame(frameTimeNs: Long) {
            val b = _binding ?: return
            if (!renderThrottleActive) return

            val minIntervalNs = 1_000_000_000L / MAX_FPS
            if (lastRenderTimeNs == 0L || frameTimeNs - lastRenderTimeNs >= minIntervalNs) {
                lastRenderTimeNs = frameTimeNs
                b.glSurfaceView.requestRender()
            }
            choreographer.postFrameCallback(this)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the binding and return root view
        _binding = FragmentOpenGlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isSupportES2()) {
            cubeRenderer = CubeRenderer()
            binding.glSurfaceView.setEGLContextClientVersion(2)
            binding.glSurfaceView.setRenderer(cubeRenderer)
            // Не крутить рендер на каждом vsync — только по запросу, не чаще MAX_FPS.
            binding.glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            renderThrottleActive = true
        }
    }

    override fun onDestroyView() {
        stopThrottledRenderLoop()
        renderThrottleActive = false
        _binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        binding.glSurfaceView.onResume()
        if (renderThrottleActive) {
            choreographer.postFrameCallback(throttledFrameRequest)
        }
    }

    override fun onPause() {
        stopThrottledRenderLoop()
        binding.glSurfaceView.onPause()
        super.onPause()
    }

    private fun stopThrottledRenderLoop() {
        choreographer.removeFrameCallback(throttledFrameRequest)
    }

    private fun isSupportES2(): Boolean {
        val activityManager =
            requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configurationInfo = activityManager.deviceConfigurationInfo
        return (configurationInfo.reqGlEsVersion >= 0x20000)
    }

    private companion object {
        private const val MAX_FPS = 60
    }
}
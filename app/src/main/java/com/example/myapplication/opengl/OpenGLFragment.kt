package com.example.myapplication.opengl

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentOpenGlBinding
class OpenGLFragment : Fragment() {
    // Nullable private binding to prevent memory leaks
    private var _binding: FragmentOpenGlBinding? = null

    // Non-null binding property for convenient access
    private val binding get() = _binding!!
    private lateinit var cubeRenderer: GLSurfaceView.Renderer


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
            cubeRenderer = CubeRenderer(requireContext())
            binding.glSurfaceView.setEGLContextClientVersion(2)
            binding.glSurfaceView.setRenderer(cubeRenderer)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        binding.glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.glSurfaceView.onPause()
    }

    private fun isSupportES2(): Boolean {
        val activityManager =
            requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configurationInfo = activityManager.deviceConfigurationInfo
        return (configurationInfo.reqGlEsVersion >= 0x20000)
    }

}
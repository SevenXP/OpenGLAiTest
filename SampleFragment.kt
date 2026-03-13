package com.example.myapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
// Replace with your actual fragment XML file name (without .xml extension)
import com.example.myapp.databinding.FragmentSampleBinding

class SampleFragment : Fragment() {
    
    // Nullable private binding to prevent memory leaks
    private var _binding: FragmentSampleBinding? = null
    
    // Non-null binding property for convenient access
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the binding and return root view
        _binding = FragmentSampleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup UI interactions using binding
        setupUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // IMPORTANT: Always nullify _binding to prevent memory leaks
        _binding = null
    }

    private fun setupUI() {
        // Example: finding views by ID - no findViewById needed!
        binding.someButton.setOnClickListener {
            // Your click handling code
        }
    }
}

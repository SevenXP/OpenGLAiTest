package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.FragmentDiceBinding
import com.example.myapplication.dice.DiceViewModel
import kotlinx.coroutines.launch

/**
 * Fragment for the dice game.
 */
class DiceFragment : Fragment() {

    private var _binding: FragmentDiceBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DiceViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        viewModel = DiceViewModel(requireContext())

        // Setup UI
        setupUI()
    }

    private fun setupUI() {
        // Roll button
        val rollButton = binding.rollButton
        rollButton.setOnClickListener {
            viewModel.roll()
        }

        // Stop button
        val stopButton = binding.stopButton
        stopButton.setOnClickListener {
            viewModel.stop()
        }

        // Observe state using lifecycleScope
        lifecycleScope.launch {
            viewModel.result.collect { result ->
                updateResultDisplay(result)
            }
        }

        lifecycleScope.launch {
            viewModel.rolling.collect { rolling ->
                updateRollingState(rolling)
            }
        }

        lifecycleScope.launch {
            viewModel.stopProgress.collect { progress ->
                updateStopProgress(progress)
            }
        }

        lifecycleScope.launch {
            viewModel.showHint.collect { showHint ->
                updateHintVisibility(showHint)
            }
        }
    }

    private fun updateResultDisplay(result: Pair<Int, Int>?) {
        val resultText = binding.resultText
        if (result != null) {
            val sum = result.first + result.second
            resultText.text = "${result.first} + ${result.second} = $sum"
            resultText.visibility = View.VISIBLE
        } else {
            resultText.visibility = View.GONE
        }
    }

    private fun updateRollingState(rolling: Boolean) {
        val rollButton = binding.rollButton
        val stopButton = binding.stopButton

        rollButton.isEnabled = !rolling
        stopButton.isEnabled = rolling
    }

    private fun updateStopProgress(progress: Float) {
        val stopButton = binding.stopButton

        if (progress > 0) {
            stopButton.alpha = 1.0f - (progress * 0.5f)
        } else {
            stopButton.alpha = 1.0f
        }
    }

    private fun updateHintVisibility(showHint: Boolean) {
        val hintText = binding.hintText
        hintText.visibility = if (showHint) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
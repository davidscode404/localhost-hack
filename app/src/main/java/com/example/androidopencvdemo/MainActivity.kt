package com.example.androidopencvdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import com.example.androidopencvdemo.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraEngine: GoblinCameraEngine
    private lateinit var imageEngine: GoblinImageEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageEngine = GoblinImageEngine(this, binding.goblinSurfaceView)
        imageEngine.callbackFPS = { fpsIn, fpsOut ->
            runOnUiThread {
                binding.goblinTextViewFPS.text = "%.1f in / %.1f out".format(fpsIn, fpsOut)
            }
        }
        cameraEngine = GoblinCameraEngine(this, imageEngine)
        lifecycle.addObserver(cameraEngine)

        binding.buttonEstimateBmi.setOnClickListener {
            estimateBmi()
        }
        binding.buttonCvDemos.setOnClickListener {
            showCvDemoMenu(it)
        }
    }

    override fun onDestroy() {
        // To avoid C++ memory leak
        imageEngine.close()
        super.onDestroy()
    }

    /// Select algo with the mode button
    fun onRadioButtonClicked(view: View?) {
        if (view == null)
            return
        val mode = view.tag.toString().toInt()   // Integer 0..4
        imageEngine.setMode(mode)
    }

    private fun estimateBmi() {
        val heightCm = binding.editTextHeight.text.toString().toDoubleOrNull()
        val weightKg = binding.editTextWeight.text.toString().toDoubleOrNull()

        if (heightCm == null || weightKg == null || heightCm <= 0.0 || weightKg <= 0.0) {
            binding.textViewBmiResult.text = getString(R.string.bmi_invalid)
            return
        }

        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        val category = when {
            bmi < 18.5 -> "Underweight"
            bmi < 25.0 -> "Healthy range"
            bmi < 30.0 -> "Overweight"
            else -> "Obesity range"
        }
        binding.textViewBmiResult.text =
            String.format(Locale.US, "BMI %.1f  |  %s", bmi, category)
    }

    private fun showCvDemoMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 0, 0, getString(R.string.algo_raw))
            menu.add(0, 1, 1, getString(R.string.algo_edges))
            menu.add(0, 2, 2, getString(R.string.algo_contours))
            menu.add(0, 3, 3, getString(R.string.algo_flow))
            menu.add(0, 4, 4, getString(R.string.algo_orb))
            setOnMenuItemClickListener { item ->
                imageEngine.setMode(item.itemId)
                true
            }
            show()
        }
    }

    companion object {
        const val TAG = "BRIANNA"
    }
}

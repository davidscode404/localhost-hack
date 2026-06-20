package com.example.androidopencvdemo

import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.PopupMenu
import com.example.androidopencvdemo.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.view.ScaleGestureDetector

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraEngine: GoblinCameraEngine
    private lateinit var imageEngine: GoblinImageEngine

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateDateTime()
            clockHandler.postDelayed(this, 1000)
        }
    }

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
        imageEngine.callbackBodyMetrics = { metrics ->
            runOnUiThread {
                displayBodyMetrics(metrics)
            }
        }
        cameraEngine = GoblinCameraEngine(this, imageEngine)
        lifecycle.addObserver(cameraEngine)

        binding.buttonCvDemos.setOnClickListener {
            showCvDemoMenu(it)
        }

        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    cameraEngine.onZoom(detector.scaleFactor)
                    return true
                }
            }
        )
        binding.goblinSurfaceView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }

        clockHandler.post(clockRunnable)
    }

    override fun onDestroy() {
        // To avoid C++ memory leak
        imageEngine.close()
        clockHandler.removeCallbacks(clockRunnable)
        super.onDestroy()
    }

    /// Select algo with the mode button
    fun onRadioButtonClicked(view: View?) {
        if (view == null)
            return
        val mode = view.tag.toString().toInt()   // Integer 0..4
        imageEngine.setMode(mode)
    }

    /// metrics = [heightCm, weightKg, bmi, confidence], per ImageProcessorWrapper.processWithMetrics
    private fun displayBodyMetrics(metrics: FloatArray) {
        val heightCm = metrics[0]
        val weightKg = metrics[1]
        val bmi = metrics[2]
        val confidence = metrics[3]

        binding.textViewHeightValue.text = String.format(Locale.US, "Height: %.0f cm", heightCm)
        binding.textViewWeightValue.text = String.format(Locale.US, "Weight: %.0f kg", weightKg)

        val isObese = bmi >= 30.0f
        binding.textViewBmiResult.text = String.format(Locale.US, "BMI %.1f", bmi)
        binding.textViewBmiResult.setTextColor(Color.parseColor(if (isObese) "#F87171" else "#4ADE80"))

        binding.textViewConfidenceValue.text =
            String.format(Locale.US, "Confidence: %.0f%%", confidence * 100)
    }

    private fun updateDateTime() {
        val now = Calendar.getInstance()
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(now.time)
        val month = SimpleDateFormat("MMMM", Locale.US).format(now.time)
        val day = now.get(Calendar.DAY_OF_MONTH)
        val year = now.get(Calendar.YEAR)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(now.time)
        binding.textViewSubtitle.text = "$dayOfWeek, $month $day${ordinalSuffix(day)}, $year, $time"
    }

    private fun ordinalSuffix(day: Int): String {
        if (day in 11..13) return "th"
        return when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
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
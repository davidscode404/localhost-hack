package com.example.androidopencvdemo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.Exception
import kotlin.math.max

class GoblinCameraEngine(
    val activity: ComponentActivity,
    val goblinImageEngine: GoblinImageEngine
) : DefaultLifecycleObserver {
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraCharacteristics: CameraCharacteristics
    private lateinit var sensorArraySize: Rect
    private lateinit var cameraSize: Size
    private var camera: CameraDevice? = null

    private var cameraInitialized: Boolean = false
    private var cameraActive: Boolean = false

    /// Zoom state (Camera2 SCALER_CROP_REGION based)
    private var captureSession: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var zoomRatio: Float = 1f
    private var maxZoom: Float = 1f

    private val cameraWorker = GoblinWorker("Camera")

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        if (allPermissionsGranted()) initCamera() else requestPermissions()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        openCamera()
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        closeCamera()
    }

    private fun selectSize(): Size {
        val ssMap: StreamConfigurationMap =
            cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val fmt: Int = ImageFormat.YUV_420_888
        val oSizes: Array<Size> = ssMap.getOutputSizes(fmt)
        if (oSizes.isEmpty()) throw Exception("Something is Wrong !")

        val preferredSize = Size(1280, 960)
        if (preferredSize in oSizes) return preferredSize

        var chosenSize = Size(0, 0)
        val maxRes = 1280
        for (sz in oSizes) {
            if (sz.width > maxRes || sz.height > maxRes) continue
            else if (max(sz.width, sz.height) > max(chosenSize.width, chosenSize.height))
                chosenSize = sz
        }
        return chosenSize
    }

    private fun initCamera() {
        cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraCharacteristics = cameraManager.getCameraCharacteristics(CAMERA_ID)
        sensorArraySize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
        maxZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f

        cameraSize = selectSize()
        goblinImageEngine.initialize(cameraSize)

        cameraInitialized = true
        openCamera()
    }

    @SuppressWarnings("MissingPermission")
    private fun openCamera() {
        if (!cameraInitialized || cameraActive || camera != null) return
        cameraActive = true
        cameraManager.openCamera(CAMERA_ID, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                this@GoblinCameraEngine.camera = camera
                startCapture()
            }
            override fun onDisconnected(camera: CameraDevice) {}
            override fun onError(camera: CameraDevice, p1: Int) {
                Log.e(TAG, "CameraDevice.StateCallback.onError")
            }
        }, cameraWorker.handler)
    }

    private fun closeCamera() {
        camera?.close()
        camera = null
        cameraActive = false
        captureSession = null
        captureRequestBuilder = null
    }

    private fun startCapture() {
        if (!cameraActive || camera == null) return
        val imageReader = goblinImageEngine.imageReader!!
        val surfaces = listOf(imageReader.surface)
        camera!!.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    val builder: CaptureRequest.Builder =
                        session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    builder.addTarget(imageReader.surface)
                    captureSession = session
                    captureRequestBuilder = builder
                    applyZoom()
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "CameraCaptureSession.StateCallback.onConfigureFailed")
                }
            }, cameraWorker.handler
        )
    }

    //==============================================================================================
    /// Call from a pinch gesture: scaleFactor > 1 zooms in, < 1 zooms out
    fun onZoom(scaleFactor: Float) {
        zoomRatio = (zoomRatio * scaleFactor).coerceIn(1f, maxZoom)
        applyZoom()
    }

    private fun applyZoom() {
        val builder = captureRequestBuilder ?: return
        val session = captureSession ?: return

        val cropW = (sensorArraySize.width() / zoomRatio).toInt()
        val cropH = (sensorArraySize.height() / zoomRatio).toInt()
        val cropX = sensorArraySize.left + (sensorArraySize.width() - cropW) / 2
        val cropY = sensorArraySize.top + (sensorArraySize.height() - cropH) / 2

        builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(cropX, cropY, cropX + cropW, cropY + cropH))
        session.setRepeatingRequest(builder.build(), null, cameraWorker.handler)
    }

    //==============================================================================================
    private val activityResultLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(activity, "Permission request denied !", Toast.LENGTH_LONG).show()
                activity.finish()
            } else {
                initCamera()
            }
        }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(activity.baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val TAG = "BRIANNA"
        const val CAMERA_ID = "0"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
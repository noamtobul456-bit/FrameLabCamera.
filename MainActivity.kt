package com.framelab.camera

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var textureView: TextureView
    private lateinit var statusText: TextView
    private lateinit var modeButton: Button
    private lateinit var isoSeek: SeekBar
    private lateinit var shutterSpinner: Spinner
    private lateinit var apertureSpinner: Spinner
    private lateinit var lensSpinner: Spinner
    private lateinit var isoValueText: TextView
    private lateinit var shutterLabel: TextView
    private lateinit var apertureInfo: TextView
    private lateinit var captureButton: Button

    private var cameraId: String? = null
    private var characteristics: CameraCharacteristics? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var manualMode = true
    private var currentIso = 200
    private var currentExposureNs = 10_000_000L
    private var currentAperture = 0f
    private var isoMin = 50
    private var isoMax = 3200
    private var exposureMinNs = 1_000_000L
    private var exposureMaxNs = 1_000_000_000L
    private var apertures = floatArrayOf()
    private val cameraIds = mutableListOf<String>()
    private val cameraNames = mutableListOf<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val shutterValues = listOf(
        "1/8000", "1/4000", "1/2000", "1/1000", "1/500", "1/250",
        "1/125", "1/60", "1/30", "1/15", "1/8", "1/4", "1/2", "1s", "2s", "4s", "8s", "15s", "30s"
    )
    private val shutterNanos = listOf(
        125_000L, 250_000L, 500_000L, 1_000_000L, 2_000_000L, 4_000_000L,
        8_000_000L, 16_666_667L, 33_333_333L, 66_666_667L, 125_000_000L,
        250_000_000L, 500_000_000L, 1_000_000_000L, 2_000_000_000L,
        4_000_000_000L, 8_000_000_000L, 15_000_000_000L, 30_000_000_000L
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        buildUi()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            discoverCameras()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    private fun buildUi() {
        window.statusBarColor = android.graphics.Color.rgb(246, 246, 248)
        window.navigationBarColor = android.graphics.Color.rgb(246, 246, 248)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(android.graphics.Color.rgb(246, 246, 248))
        }
        val title = TextView(this).apply {
            text = "FrameLab Camera"
            textSize = 25f
            setTextColor(android.graphics.Color.rgb(25, 25, 28))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        page.addView(title)
        statusText = TextView(this).apply {
            text = "Requesting camera access…"
            textSize = 12f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, dp(3), 0, dp(8))
        }
        page.addView(statusText)

        textureView = TextureView(this)
        page.addView(textureView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        ).apply { bottomMargin = dp(10) })

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        modeButton = Button(this).apply {
            text = "MANUAL"
            setOnClickListener {
                manualMode = !manualMode
                text = if (manualMode) "MANUAL" else "AUTO"
                updateControlsEnabled()
                applyRepeatingRequest()
            }
        }
        modeRow.addView(modeButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        lensSpinner = Spinner(this)
        modeRow.addView(lensSpinner, LinearLayout.LayoutParams(0, dp(46), 1.2f).apply { leftMargin = dp(8) })
        page.addView(modeRow)

        val isoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        isoValueText = TextView(this).apply { text = "ISO 200"; textSize = 14f; setTextColor(android.graphics.Color.BLACK) }
        isoRow.addView(isoValueText, LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT))
        isoSeek = SeekBar(this).apply {
            max = 1000
            progress = 200
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentIso = isoMin + ((isoMax - isoMin) * progress / 1000)
                    isoValueText.text = "ISO $currentIso"
                    if (fromUser) applyRepeatingRequest()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        isoRow.addView(isoSeek, LinearLayout.LayoutParams(0, dp(44), 1f))
        page.addView(isoRow)

        val shutterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        shutterLabel = TextView(this).apply { text = "Shutter"; textSize = 14f; setTextColor(android.graphics.Color.BLACK) }
        shutterRow.addView(shutterLabel, LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT))
        shutterSpinner = Spinner(this)
        shutterSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shutterValues)
        shutterSpinner.setSelection(8)
        shutterSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentExposureNs = shutterNanos[position].coerceIn(exposureMinNs, exposureMaxNs)
                applyRepeatingRequest()
            }
        }
        shutterRow.addView(shutterSpinner, LinearLayout.LayoutParams(0, dp(44), 1f))
        page.addView(shutterRow)

        val apertureRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        apertureInfo = TextView(this).apply { text = "Aperture"; textSize = 14f; setTextColor(android.graphics.Color.BLACK) }
        apertureRow.addView(apertureInfo, LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT))
        apertureSpinner = Spinner(this)
        apertureRow.addView(apertureSpinner, LinearLayout.LayoutParams(0, dp(44), 1f))
        apertureSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (apertures.isNotEmpty() && position in apertures.indices) {
                    currentAperture = apertures[position]
                    applyRepeatingRequest()
                }
            }
        }
        page.addView(apertureRow)

        captureButton = Button(this).apply {
            text = "●   Capture photo"
            textSize = 16f
            setOnClickListener { capturePhoto() }
        }
        page.addView(captureButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
        })
        val note = TextView(this).apply {
            text = "Photos save to Gallery. Aperture choices depend on the selected camera lens."
            textSize = 11f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, dp(5), 0, 0)
        }
        page.addView(note)
        setContentView(page)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (cameraId != null) openCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                configureTransform(width, height)
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun discoverCameras() {
        try {
            cameraIds.clear()
            cameraNames.clear()
            for (id in cameraManager.cameraIdList) {
                val c = cameraManager.getCameraCharacteristics(id)
                if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraIds.add(id)
                    val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val label = when {
                        focal == null || focal.isEmpty() -> "Rear lens $id"
                        else -> "${focal[0]}mm • $id"
                    }
                    cameraNames.add(label)
                }
            }
            if (cameraIds.isEmpty()) {
                statusText.text = "No rear camera was found."
                return
            }
            lensSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cameraNames)
            lensSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position in cameraIds.indices && cameraId != cameraIds[position]) {
                        cameraId = cameraIds[position]
                        closeCamera()
                        loadCharacteristics()
                        if (textureView.isAvailable) openCamera()
                    }
                }
            }
            cameraId = cameraIds.first()
            loadCharacteristics()
            if (textureView.isAvailable) openCamera()
        } catch (e: Exception) {
            statusText.text = "Camera setup failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun loadCharacteristics() {
        val id = cameraId ?: return
        characteristics = cameraManager.getCameraCharacteristics(id)
        val c = characteristics ?: return
        val range = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        if (range != null) {
            isoMin = range.lower
            isoMax = range.upper
            currentIso = currentIso.coerceIn(isoMin, isoMax)
        }
        val expRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        if (expRange != null) {
            exposureMinNs = expRange.lower
            exposureMaxNs = expRange.upper
            currentExposureNs = currentExposureNs.coerceIn(exposureMinNs, exposureMaxNs)
        }
        apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: floatArrayOf()
        val apertureLabels = if (apertures.isNotEmpty()) {
            apertures.map { "f/${String.format(Locale.US, "%.1f", it)}" }
        } else listOf("Aperture unavailable")
        apertureSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apertureLabels)
        if (apertures.isNotEmpty()) {
            val nearest = apertures.indices.minByOrNull { kotlin.math.abs(apertures[it] - currentAperture) } ?: 0
            apertureSpinner.setSelection(nearest)
            currentAperture = apertures[nearest]
            apertureInfo.text = if (apertures.size > 1) "Aperture" else "Fixed f-number"
        } else {
            apertureInfo.text = "Aperture"
        }
        statusText.text = "ISO $isoMin–$isoMax • Manual camera controls available where supported"
        isoSeek.progress = (((currentIso - isoMin).toFloat() / max(1, isoMax - isoMin)) * 1000).toInt()
        isoValueText.text = "ISO $currentIso"
        updateControlsEnabled()
    }

    private fun openCamera() {
        val id = cameraId ?: return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    startPreview()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (device === camera) device = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (device === camera) device = null
                    runOnUiThread { statusText.text = "Camera error ($error). Try another lens." }
                }
            }, null)
        } catch (e: Exception) {
            statusText.text = "Unable to open camera: ${e.message ?: "unknown error"}"
        }
    }

    private fun startPreview() {
        val camera = device ?: return
        val texture = textureView.surfaceTexture ?: return
        val size = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java)?.minByOrNull { kotlin.math.abs(it.width.toFloat()/it.height - 1.5f) }
            ?: android.util.Size(1280, 720)
        texture.setDefaultBufferSize(size.width, size.height)
        val previewSurface = Surface(texture)
        imageReader?.close()
        imageReader = ImageReader.newInstance(2000, 1500, ImageFormat.JPEG, 2)
        val photoSurface = imageReader!!.surface
        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                saveToGallery(bytes)
            } finally {
                image.close()
            }
        }, executor)
        try {
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            camera.createCaptureSession(listOf(previewSurface, photoSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    session = configured
                    applyRepeatingRequest()
                }
                override fun onConfigureFailed(configured: CameraCaptureSession) {
                    runOnUiThread { statusText.text = "Could not configure camera preview." }
                }
            }, null)
        } catch (e: Exception) {
            runOnUiThread { statusText.text = "Preview error: ${e.message ?: "unknown error"}" }
        }
    }

    private fun applyRepeatingRequest() {
        val s = session ?: return
        val builder = previewBuilder ?: return
        try {
            if (manualMode) {
                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso.coerceIn(isoMin, isoMax))
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureNs.coerceIn(exposureMinNs, exposureMaxNs))
                if (apertures.isNotEmpty()) {
                    builder.set(CaptureRequest.LENS_APERTURE, currentAperture)
                }
            } else {
                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            s.setRepeatingRequest(builder.build(), null, null)
        } catch (e: Exception) {
            runOnUiThread { statusText.text = "Some settings are not supported by this lens." }
        }
    }

    private fun capturePhoto() {
        val camera = device ?: run {
            statusText.text = "Camera is not ready yet."
            return
        }
        val s = session ?: run {
            statusText.text = "Camera is still starting."
            return
        }
        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builder.addTarget(imageReader!!.surface)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            if (manualMode) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso.coerceIn(isoMin, isoMax))
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureNs.coerceIn(exposureMinNs, exposureMaxNs))
                if (apertures.isNotEmpty()) builder.set(CaptureRequest.LENS_APERTURE, currentAperture)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            val rotation = windowManager.defaultDisplay.rotation
            val orientation = when (rotation) {
                Surface.ROTATION_0 -> 90
                Surface.ROTATION_90 -> 0
                Surface.ROTATION_180 -> 270
                else -> 180
            }
            builder.set(CaptureRequest.JPEG_ORIENTATION, orientation)
            captureButton.isEnabled = false
            s.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    runOnUiThread {
                        captureButton.isEnabled = true
                        statusText.text = "Photo captured. Saving to Gallery…"
                    }
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    runOnUiThread {
                        captureButton.isEnabled = true
                        statusText.text = "Capture failed. Try Auto mode or another lens."
                    }
                }
            }, null)
        } catch (e: Exception) {
            captureButton.isEnabled = true
            statusText.text = "Capture error: ${e.message ?: "unknown error"}"
        }
    }

    private fun saveToGallery(bytes: ByteArray) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "FrameLab_$stamp.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FrameLab")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) throw IllegalStateException("Gallery could not create a photo entry")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not write photo data")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finish = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(uri, finish, null, null)
            }
            runOnUiThread { statusText.text = "Saved to Gallery → Pictures/FrameLab" }
        } catch (e: Exception) {
            runOnUiThread { statusText.text = "Photo captured, but saving failed: ${e.message ?: "unknown error"}" }
        }
    }

    private fun updateControlsEnabled() {
        isoSeek.isEnabled = manualMode
        shutterSpinner.isEnabled = manualMode
        apertureSpinner.isEnabled = manualMode && apertures.size > 1
        shutterLabel.alpha = if (manualMode) 1f else 0.45f
        isoValueText.alpha = if (manualMode) 1f else 0.45f
        apertureInfo.alpha = if (manualMode) 1f else 0.45f
    }

    private fun configureTransform(width: Int, height: Int) {
        // TextureView uses the device display orientation; camera preview remains functional without custom crop transforms.
    }

    private fun closeCamera() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { device?.close() } catch (_: Exception) {}
        device = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            discoverCameras()
        } else {
            statusText.text = "Camera permission is needed to use FrameLab Camera."
        }
    }

    override fun onPause() {
        closeCamera()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::textureView.isInitialized && textureView.isAvailable && cameraId != null &&
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && device == null) {
            openCamera()
        }
    }

    override fun onDestroy() {
        closeCamera()
        executor.shutdown()
        super.onDestroy()
    }
}

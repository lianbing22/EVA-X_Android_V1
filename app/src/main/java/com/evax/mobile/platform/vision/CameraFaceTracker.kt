package com.evax.mobile.platform.vision

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.PointF
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.FaceDetector
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class FaceTrackingState(
    val isCameraActive: Boolean = false,
    val hasPermission: Boolean = false,
    val faceDetected: Boolean = false,
    /** Normalized horizontal face offset in [-1f, 1f]: -1 = left edge, +1 = right edge */
    val faceX: Float = 0f,
    /** Normalized vertical face offset in [-1f, 1f]: -1 = top edge, +1 = bottom edge */
    val faceY: Float = 0f,
    /** Relative face size/proximity in [0f, 1f] */
    val faceScale: Float = 0.3f,
    val trackingSource: String = "未开启",
)

/**
 * Real-time front-camera face tracker for EVA-X desk companion gaze following.
 *
 * Uses a dual-engine pipeline:
 * 1. Hardware ISP Face Detection ([CaptureResult.STATISTICS_FACES]) at full sensor frame rate.
 * 2. Software [android.media.FaceDetector] + luminance centroid fallback on rotated upright
 *    mirror frames from a low-res YUV [ImageReader], ensuring 100% compatibility across phones.
 */
class CameraFaceTracker(
    private val context: Context,
) {
    private val _state = MutableStateFlow(FaceTrackingState())
    val state: StateFlow<FaceTrackingState> = _state.asStateFlow()

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var sensorOrientation: Int = 270
    private var isFrontFacing: Boolean = true
    private var lastHardwareFaceTimestampMs: Long = 0L
    private var lastSoftwareFrameTimestampMs: Long = 0L
    private var previousLumaGrid: FloatArray? = null
    private var smoothedX: Float = 0f
    private var smoothedY: Float = 0f

    fun onPermissionChanged(granted: Boolean) {
        _state.value = _state.value.copy(hasPermission = granted)
        if (!granted) {
            stop()
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (cameraDevice != null) return
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val cameraId = selectCameraId(manager) ?: return

        try {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            isFrontFacing = facing == CameraCharacteristics.LENS_FACING_FRONT

            val thread = HandlerThread("EvaXFaceTrackerThread").also { it.start() }
            val handler = Handler(thread.looper)
            cameraThread = thread
            cameraHandler = handler

            val reader = ImageReader.newInstance(160, 120, ImageFormat.YUV_420_888, 2)
            reader.setOnImageAvailableListener({ ir ->
                processSoftwareFrame(ir)
            }, handler)
            imageReader = reader

            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        _state.value = _state.value.copy(
                            isCameraActive = true,
                            hasPermission = true,
                            trackingSource = "视觉感知中",
                        )
                        createSession(camera, characteristics, reader.surface, handler)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                        _state.value = _state.value.copy(
                            isCameraActive = false,
                            faceDetected = false,
                        )
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        _state.value = _state.value.copy(
                            isCameraActive = false,
                            faceDetected = false,
                        )
                    }
                },
                handler,
            )
        } catch (_: Throwable) {
            stop()
        }
    }

    fun stop() {
        try {
            captureSession?.close()
        } catch (_: Throwable) {
        }
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Throwable) {
        }
        cameraDevice = null

        try {
            imageReader?.close()
        } catch (_: Throwable) {
        }
        imageReader = null

        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null

        _state.value = _state.value.copy(
            isCameraActive = false,
            faceDetected = false,
        )
    }

    private fun selectCameraId(manager: CameraManager): String? {
        val ids = manager.cameraIdList
        for (id in ids) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                return id
            }
        }
        return ids.firstOrNull()
    }

    private fun createSession(
        camera: CameraDevice,
        characteristics: CameraCharacteristics,
        surface: Surface,
        handler: Handler,
    ) {
        try {
            val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val availableFaceModes = characteristics.get(
                CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES,
            ) ?: intArrayOf()

            val bestFaceMode = when {
                availableFaceModes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL) ->
                    CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL
                availableFaceModes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) ->
                    CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE
                else -> CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(surface)
                                set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                                )
                                set(
                                    CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON,
                                )
                                if (bestFaceMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
                                    set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, bestFaceMode)
                                }
                            }

                            session.setRepeatingRequest(
                                requestBuilder.build(),
                                object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult,
                                    ) {
                                        val faces = result.get(CaptureResult.STATISTICS_FACES)
                                        if (!faces.isNullOrEmpty() && activeArray != null && activeArray.width() > 0 && activeArray.height() > 0) {
                                            val primaryFace = faces.maxByOrNull {
                                                it.bounds.width() * it.bounds.height()
                                            } ?: faces[0]

                                            val rawU = ((primaryFace.bounds.exactCenterX() - activeArray.left) / activeArray.width()).coerceIn(0f, 1f)
                                            val rawV = ((primaryFace.bounds.exactCenterY() - activeArray.top) / activeArray.height()).coerceIn(0f, 1f)
                                            val mapped = mapSensorToScreen(rawU, rawV)
                                            val scale = (primaryFace.bounds.width().toFloat() / activeArray.width().toFloat()).coerceIn(0.1f, 0.9f)

                                            lastHardwareFaceTimestampMs = System.currentTimeMillis()
                                            updateFaceTarget(
                                                normX = (mapped.x - 0.5f) * 2f,
                                                normY = (mapped.y - 0.5f) * 2f,
                                                scale = scale,
                                                source = "ISP 人脸锁定",
                                            )
                                        }
                                    }
                                },
                                handler,
                            )
                        } catch (_: Throwable) {
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                handler,
            )
        } catch (_: Throwable) {
        }
    }

    private fun processSoftwareFrame(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Throwable) {
            null
        } ?: return

        try {
            val now = System.currentTimeMillis()
            // Skip if hardware ISP face detection is actively reporting or throttle software pass to ~7 fps
            if (now - lastHardwareFaceTimestampMs < 450L || now - lastSoftwareFrameTimestampMs < 140L) {
                return
            }
            lastSoftwareFrameTimestampMs = now

            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val srcW = image.width
            val srcH = image.height

            // Downsample to an upright 80x80 RGB_565 bitmap for Android FaceDetector & motion tracking
            val targetSize = 80
            val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.RGB_565)
            val currentGrid = FloatArray(targetSize * targetSize)

            for (ty in 0 until targetSize) {
                val screenY = (ty + 0.5f) / targetSize
                for (tx in 0 until targetSize) {
                    val screenX = (tx + 0.5f) / targetSize
                    val sensorPt = mapScreenToSensor(screenX, screenY)
                    val sx = (sensorPt.x * (srcW - 1)).toInt().coerceIn(0, srcW - 1)
                    val sy = (sensorPt.y * (srcH - 1)).toInt().coerceIn(0, srcH - 1)
                    val index = sy * rowStride + sx * pixelStride
                    val luma = if (index in 0 until buffer.limit()) {
                        buffer.get(index).toInt() and 0xFF
                    } else {
                        128
                    }
                    currentGrid[ty * targetSize + tx] = luma.toFloat()
                    bitmap.setPixel(tx, ty, Color.rgb(luma, luma, luma))
                }
            }

            val detector = FaceDetector(targetSize, targetSize, 1)
            val detected = arrayOfNulls<FaceDetector.Face>(1)
            val count = detector.findFaces(bitmap, detected)
            bitmap.recycle()

            if (count > 0 && detected[0] != null) {
                val face = detected[0]!!
                val mid = PointF()
                face.getMidPoint(mid)
                val normX = ((mid.x / targetSize) - 0.5f) * 2f
                val normY = ((mid.y / targetSize) - 0.5f) * 2f
                val scale = ((face.eyesDistance() * 2.4f) / targetSize).coerceIn(0.15f, 0.85f)
                previousLumaGrid = currentGrid
                updateFaceTarget(
                    normX = normX,
                    normY = normY,
                    scale = scale,
                    source = "视觉人脸锁定",
                )
                return
            }

            // Motion centroid fallback when user moves head in front of camera
            val prev = previousLumaGrid
            previousLumaGrid = currentGrid
            if (prev != null) {
                var weightedX = 0f
                var weightedY = 0f
                var totalWeight = 0f
                for (ty in 8 until (targetSize - 8)) {
                    for (tx in 8 until (targetSize - 8)) {
                        val idx = ty * targetSize + tx
                        val diff = abs(currentGrid[idx] - prev[idx])
                        if (diff > 14f) {
                            weightedX += tx * diff
                            weightedY += ty * diff
                            totalWeight += diff
                        }
                    }
                }
                if (totalWeight > 950f) {
                    val cx = weightedX / totalWeight
                    val cy = weightedY / totalWeight
                    val normX = ((cx / targetSize) - 0.5f) * 2f
                    val normY = ((cy / targetSize) - 0.5f) * 2f
                    updateFaceTarget(
                        normX = normX,
                        normY = normY,
                        scale = 0.35f,
                        source = "动态视觉跟随",
                    )
                } else if (now - lastHardwareFaceTimestampMs > 2_800L) {
                    // Decay face lock gently if no face or motion for a while
                    _state.value = _state.value.copy(
                        faceDetected = false,
                        trackingSource = "视觉感知中",
                    )
                }
            }
        } catch (_: Throwable) {
        } finally {
            image.close()
        }
    }

    private fun updateFaceTarget(
        normX: Float,
        normY: Float,
        scale: Float,
        source: String,
    ) {
        // Exponential smoothing for lifelike gimbal-like eye gaze
        val clampedX = (normX * 1.25f).coerceIn(-0.85f, 0.85f)
        val clampedY = (normY * 1.15f).coerceIn(-0.65f, 0.65f)
        smoothedX = smoothedX * 0.45f + clampedX * 0.55f
        smoothedY = smoothedY * 0.45f + clampedY * 0.55f

        _state.value = _state.value.copy(
            isCameraActive = true,
            hasPermission = true,
            faceDetected = true,
            faceX = smoothedX,
            faceY = smoothedY,
            faceScale = scale,
            trackingSource = source,
        )
    }

    /**
     * Maps normalized sensor coordinates (u, v) in [0, 1] to upright, mirrored screen coordinates (x, y) in [0, 1].
     */
    private fun mapSensorToScreen(u: Float, v: Float): PointF {
        // Step 1: Rotate clockwise by sensorOrientation to natural portrait upright
        var xp: Float
        var yp: Float
        when ((sensorOrientation % 360 + 360) % 360) {
            90 -> {
                xp = 1f - v
                yp = u
            }
            180 -> {
                xp = 1f - u
                yp = 1f - v
            }
            270 -> {
                xp = v
                yp = 1f - u
            }
            else -> {
                xp = u
                yp = v
            }
        }
        // Mirror horizontally for front-facing camera
        if (isFrontFacing) {
            xp = 1f - xp
        }

        // Step 2: Adjust for current device display rotation (Portrait vs Landscape)
        return when (currentDisplayRotation()) {
            Surface.ROTATION_90 -> PointF(yp, 1f - xp)
            Surface.ROTATION_270 -> PointF(1f - yp, xp)
            Surface.ROTATION_180 -> PointF(1f - xp, 1f - yp)
            else -> PointF(xp, yp)
        }
    }

    /**
     * Inverse mapping from upright screen coordinates (x, y) in [0, 1] back to sensor coordinates (u, v) in [0, 1].
     */
    private fun mapScreenToSensor(x: Float, y: Float): PointF {
        var xp: Float
        var yp: Float
        when (currentDisplayRotation()) {
            Surface.ROTATION_90 -> {
                xp = 1f - y
                yp = x
            }
            Surface.ROTATION_270 -> {
                xp = y
                yp = 1f - x
            }
            Surface.ROTATION_180 -> {
                xp = 1f - x
                yp = 1f - y
            }
            else -> {
                xp = x
                yp = y
            }
        }

        if (isFrontFacing) {
            xp = 1f - xp
        }

        return when ((sensorOrientation % 360 + 360) % 360) {
            90 -> PointF(yp, 1f - xp)
            180 -> PointF(1f - xp, 1f - yp)
            270 -> PointF(1f - yp, xp)
            else -> PointF(xp, yp)
        }
    }

    private fun currentDisplayRotation(): Int {
        return try {
            @Suppress("DEPRECATION")
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        } catch (_: Throwable) {
            Surface.ROTATION_0
        }
    }
}

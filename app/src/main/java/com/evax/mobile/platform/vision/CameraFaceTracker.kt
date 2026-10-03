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
import android.media.Image
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
    val faceScale: Float = 0.35f,
    val trackingSource: String = "未开启",
)

/**
 * Triple-Engine Real-Time Front-Camera Face Tracker for EVA-X:
 * 1. Hardware ISP Face Detection ([CaptureResult.STATISTICS_FACES]) at 30fps.
 * 2. YCbCr Skin-Tone Face Blob & Android [FaceDetector] upright analysis (~10fps).
 * 3. Luma Motion Centroid fusion so even in dim light or side-profile, head movement is tracked continuously.
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
                                            val scale = (primaryFace.bounds.width().toFloat() / activeArray.width().toFloat()).coerceIn(0.15f, 0.90f)

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
            // Skip software pass if hardware ISP face detection just reported within 350ms, or throttle to ~10fps
            if (now - lastHardwareFaceTimestampMs < 350L || now - lastSoftwareFrameTimestampMs < 95L) {
                return
            }
            lastSoftwareFrameTimestampMs = now

            val planes = image.planes
            if (planes.isEmpty()) return

            val targetSize = 64
            val currentGrid = FloatArray(targetSize * targetSize)

            // Track skin-tone centroid in YCbCr space + build grayscale bitmap for FaceDetector
            val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.RGB_565)
            var skinWeightedX = 0f
            var skinWeightedY = 0f
            var skinTotalWeight = 0f

            for (ty in 0 until targetSize) {
                val screenY = (ty + 0.5f) / targetSize
                // Center bias weight so background walls at extreme edges have lower weight
                val wy = 1f - 0.35f * abs(screenY - 0.5f)
                for (tx in 0 until targetSize) {
                    val screenX = (tx + 0.5f) / targetSize
                    val wx = 1f - 0.25f * abs(screenX - 0.5f)
                    val sensorPt = mapScreenToSensor(screenX, screenY)

                    val yuv = sampleYuv(image, sensorPt.x, sensorPt.y)
                    val yVal = yuv[0]
                    val uVal = yuv[1] // Cb
                    val vVal = yuv[2] // Cr

                    currentGrid[ty * targetSize + tx] = yVal.toFloat()
                    bitmap.setPixel(tx, ty, Color.rgb(yVal, yVal, yVal))

                    // Standard human skin-tone cluster in YCbCr: Y in 45..235, Cb in 77..127, Cr in 133..178
                    if (yVal in 45..235 && uVal in 77..127 && vVal in 133..178) {
                        // Closeness to core skin chrominance (Cb=105, Cr=152)
                        val chromaAffinity = (1f - (abs(uVal - 105) / 30f) - (abs(vVal - 152) / 30f)).coerceIn(0.2f, 1f)
                        val w = chromaAffinity * wx * wy
                        skinWeightedX += tx * w
                        skinWeightedY += ty * w
                        skinTotalWeight += w
                    }
                }
            }

            // 1. Try Android FaceDetector first for exact inter-eye midpoint
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
                val scale = ((face.eyesDistance() * 2.4f) / targetSize).coerceIn(0.18f, 0.85f)
                previousLumaGrid = currentGrid
                updateFaceTarget(
                    normX = normX,
                    normY = normY,
                    scale = scale,
                    source = "五官精准锁定",
                )
                return
            }

            // 2. Compute motion centroid from frame difference
            val prev = previousLumaGrid
            previousLumaGrid = currentGrid
            var motionX = 0f
            var motionY = 0f
            var motionWeight = 0f
            if (prev != null) {
                for (ty in 4 until (targetSize - 4)) {
                    for (tx in 4 until (targetSize - 4)) {
                        val idx = ty * targetSize + tx
                        val diff = abs(currentGrid[idx] - prev[idx])
                        if (diff > 10f) {
                            motionX += tx * diff
                            motionY += ty * diff
                            motionWeight += diff
                        }
                    }
                }
            }

            // 3. Fuse YCbCr skin-tone face blob with motion centroid
            val hasSkinFaceBlob = skinTotalWeight > 18f
            val hasMotion = motionWeight > 360f

            if (hasSkinFaceBlob && hasMotion) {
                val sx = (skinWeightedX / skinTotalWeight) / targetSize
                val sy = (skinWeightedY / skinTotalWeight) / targetSize
                val mx = (motionX / motionWeight) / targetSize
                val my = (motionY / motionWeight) / targetSize
                val fusedX = (sx * 0.65f + mx * 0.35f - 0.5f) * 2f
                val fusedY = (sy * 0.65f + my * 0.35f - 0.5f) * 2f
                updateFaceTarget(
                    normX = fusedX,
                    normY = fusedY,
                    scale = (skinTotalWeight / (targetSize * targetSize * 0.35f)).coerceIn(0.2f, 0.8f),
                    source = "人脸实时跟随",
                )
            } else if (hasSkinFaceBlob) {
                val sx = (skinWeightedX / skinTotalWeight) / targetSize
                val sy = (skinWeightedY / skinTotalWeight) / targetSize
                updateFaceTarget(
                    normX = (sx - 0.5f) * 2f,
                    normY = (sy - 0.5f) * 2f,
                    scale = (skinTotalWeight / (targetSize * targetSize * 0.35f)).coerceIn(0.2f, 0.8f),
                    source = "人脸实时跟随",
                )
            } else if (hasMotion) {
                val mx = (motionX / motionWeight) / targetSize
                val my = (motionY / motionWeight) / targetSize
                updateFaceTarget(
                    normX = (mx - 0.5f) * 2f,
                    normY = (my - 0.5f) * 2f,
                    scale = 0.35f,
                    source = "动态视觉跟随",
                )
            }
        } catch (_: Throwable) {
        } finally {
            image.close()
        }
    }

    private fun sampleYuv(image: Image, normU: Float, normV: Float): IntArray {
        val w = image.width
        val h = image.height
        val x = (normU * (w - 1)).toInt().coerceIn(0, w - 1)
        val y = (normV * (h - 1)).toInt().coerceIn(0, h - 1)

        val yPlane = image.planes[0]
        val yIdx = y * yPlane.rowStride + x * yPlane.pixelStride
        val yBuf = yPlane.buffer
        val yVal = if (yIdx in 0 until yBuf.limit()) (yBuf.get(yIdx).toInt() and 0xFF) else 128

        if (image.planes.size < 3) {
            return intArrayOf(yVal, 105, 152)
        }

        val uvX = x / 2
        val uvY = y / 2
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uIdx = uvY * uPlane.rowStride + uvX * uPlane.pixelStride
        val vIdx = uvY * vPlane.rowStride + uvX * vPlane.pixelStride
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uVal = if (uIdx in 0 until uBuf.limit()) (uBuf.get(uIdx).toInt() and 0xFF) else 128
        val vVal = if (vIdx in 0 until vBuf.limit()) (vBuf.get(vIdx).toInt() and 0xFF) else 128

        return intArrayOf(yVal, uVal, vVal)
    }

    private fun updateFaceTarget(
        normX: Float,
        normY: Float,
        scale: Float,
        source: String,
    ) {
        // Amplify sensitivity slightly so even subtle head movements on a desk stand produce expressive eye motion
        val clampedX = (normX * 1.45f).coerceIn(-0.90f, 0.90f)
        val clampedY = (normY * 1.35f).coerceIn(-0.75f, 0.75f)
        smoothedX = smoothedX * 0.38f + clampedX * 0.62f
        smoothedY = smoothedY * 0.38f + clampedY * 0.62f

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

    private fun mapSensorToScreen(u: Float, v: Float): PointF {
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
        if (isFrontFacing) {
            xp = 1f - xp
        }

        return when (currentDisplayRotation()) {
            Surface.ROTATION_90 -> PointF(yp, 1f - xp)
            Surface.ROTATION_270 -> PointF(1f - yp, xp)
            Surface.ROTATION_180 -> PointF(1f - xp, 1f - yp)
            else -> PointF(xp, yp)
        }
    }

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

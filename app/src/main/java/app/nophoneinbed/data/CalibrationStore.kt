package app.nophoneinbed.data

import android.content.Context
import app.nophoneinbed.domain.BedCalibration
import app.nophoneinbed.domain.NPoint
import java.util.Base64

interface CalibrationStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class SharedPreferencesCalibrationStorage(context: Context) : CalibrationStorage {
    private val preferences = context.getSharedPreferences("bed_calibration", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = preferences.getString(key, null)
    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}

class CalibrationStore(private val storage: CalibrationStorage) {
    constructor(context: Context) : this(SharedPreferencesCalibrationStorage(context))

    fun save(calibration: BedCalibration) {
        val corners = calibration.mattressCorners.flatMap { listOf(it.x, it.y) }.joinToString(",")
        val gravity = calibration.gravity.joinToString(",")
        val offset = "${calibration.manualUpperOffset.x},${calibration.manualUpperOffset.y}"
        val camera = Base64.getUrlEncoder().withoutPadding().encodeToString(calibration.cameraId.toByteArray())
        storage.putString(
            KEY,
            "version=$VERSION;width=${calibration.widthMeters};length=${calibration.lengthMeters};" +
                "height=${calibration.heightMeters};corners=$corners;gravity=$gravity;" +
                "offset=$offset;camera=$camera",
        )
    }

    fun load(expectedCameraId: String): Result<BedCalibration?> = runCatching {
        val encoded = storage.getString(KEY) ?: return@runCatching null
        val fields = encoded.split(';').associate { segment ->
            val separator = segment.indexOf('=')
            require(separator > 0) { "Calibration data is malformed" }
            segment.substring(0, separator) to segment.substring(separator + 1)
        }
        require(fields.getValue("version").toInt() == VERSION) { "Unknown calibration version" }
        val cameraId = String(Base64.getUrlDecoder().decode(fields.getValue("camera")))
        require(cameraId == expectedCameraId) { "Camera changed; recalibration is required" }
        val corners = fields.getValue("corners").floatList(8).chunked(2).map { NPoint(it[0], it[1]) }
        val gravity = fields.getValue("gravity").floatList(3)
        val offset = fields.getValue("offset").floatList(2)
        BedCalibration.create(
            widthMeters = fields.getValue("width").finiteFloat(),
            lengthMeters = fields.getValue("length").finiteFloat(),
            heightMeters = fields.getValue("height").finiteFloat(),
            corners = corners,
            gravity = gravity,
            manualUpperOffset = NPoint(offset[0], offset[1]),
            cameraId = cameraId,
        ).getOrThrow()
    }

    fun clear() = storage.remove(KEY)

    private fun String.floatList(expectedSize: Int): List<Float> = split(',').map { it.finiteFloat() }.also {
        require(it.size == expectedSize) { "Calibration field has the wrong size" }
    }

    private fun String.finiteFloat(): Float = toFloat().also {
        require(it.isFinite()) { "Calibration contains a non-finite number" }
    }

    companion object {
        const val KEY = "calibration_v1"
        private const val VERSION = 1
        const val DEFAULT_CAMERA_ID = "0"
    }
}

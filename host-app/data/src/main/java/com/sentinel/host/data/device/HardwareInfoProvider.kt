package com.sentinel.host.data.device

import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware-level device information and identity provider.
 * Extracts telemetry directly from physical device hardware, kernel, and Android subsystem.
 */
data class HardwareInfo(
    val hardwareId: String,          // Android ID (lowercase hex string)
    val hardwareSerial: String,      // Hardware serial or "UNKNOWN"
    val manufacturer: String,        // Build.MANUFACTURER (e.g. "vivo")
    val brand: String,               // Build.BRAND (e.g. "vivo", "iQOO")
    val model: String,               // Build.MODEL (e.g. "I2401")
    val device: String,              // Build.DEVICE (e.g. "I2401")
    val product: String,             // Build.PRODUCT (e.g. "I2401i")
    val board: String,               // Build.BOARD (e.g. "sun")
    val hardware: String,            // Build.HARDWARE (e.g. "qcom")
    val supportedAbis: String,       // Build.SUPPORTED_ABIS (e.g. "arm64-v8a")
    val socManufacturer: String,     // Build.SOC_MANUFACTURER (API 31+)
    val socModel: String,            // Build.SOC_MODEL (API 31+)
    val androidVersion: String,      // Build.VERSION.RELEASE (e.g. "16")
    val sdkInt: Int,                 // Build.VERSION.SDK_INT (e.g. 36)
    val securityPatch: String,       // Build.VERSION.SECURITY_PATCH
    val fingerprint: String,         // Build.FINGERPRINT
    val screenResolution: String,    // e.g. "1080 x 2400"
    val screenDpi: Int,              // e.g. 480
    val totalRamBytes: Long,         // Total physical RAM in bytes
    val totalStorageBytes: Long,     // Total internal storage in bytes
    val hardwareCallsign: String     // Deterministic hardware callsign e.g. "HOST-VIVO-CFAE"
)

@Singleton
class HardwareInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getHardwareInfo(): HardwareInfo {
        val contentResolver = context.contentResolver
        val rawAndroidId = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Exception) {
            ""
        }
        val hardwareId = rawAndroidId.lowercase().ifBlank { "unknown_hw" }

        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val brand = Build.BRAND ?: manufacturer
        val model = Build.MODEL ?: "Unknown"
        val device = Build.DEVICE ?: "Unknown"
        val product = Build.PRODUCT ?: "Unknown"
        val board = Build.BOARD ?: "Unknown"
        val hardware = Build.HARDWARE ?: "Unknown"
        val supportedAbis = Build.SUPPORTED_ABIS?.joinToString(", ") ?: "unknown"

        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { Build.SOC_MANUFACTURER ?: "" } catch (_: Throwable) { "" }
        } else {
            ""
        }

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { Build.SOC_MODEL ?: "" } catch (_: Throwable) { "" }
        } else {
            ""
        }

        val androidVersion = Build.VERSION.RELEASE ?: "Unknown"
        val sdkInt = Build.VERSION.SDK_INT
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH ?: ""
        } else {
            ""
        }

        val fingerprint = Build.FINGERPRINT ?: ""

        val serial = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Build.getSerial()
                } catch (_: SecurityException) {
                    Build.SERIAL ?: "UNKNOWN"
                }
            } else {
                Build.SERIAL ?: "UNKNOWN"
            }
        } catch (_: Throwable) {
            "UNKNOWN"
        }

        // Display metrics from resources
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenResolution = "${displayMetrics.widthPixels} x ${displayMetrics.heightPixels}"
        val screenDpi = displayMetrics.densityDpi

        // Memory info
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamBytes = memInfo.totalMem

        // Storage info
        val totalStorageBytes = try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }

        // Hardware callsign: e.g. "HOST-VIVO-CFAE"
        val oemLetters = manufacturer.uppercase().filter { it.isLetter() }.take(4).ifBlank { "UNIT" }
        val shortId = if (hardwareId.length >= 4) hardwareId.takeLast(4).uppercase() else "0001"
        val hardwareCallsign = "HOST-$oemLetters-$shortId"

        return HardwareInfo(
            hardwareId = hardwareId,
            hardwareSerial = serial,
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            device = device,
            product = product,
            board = board,
            hardware = hardware,
            supportedAbis = supportedAbis,
            socManufacturer = socManufacturer,
            socModel = socModel,
            androidVersion = androidVersion,
            sdkInt = sdkInt,
            securityPatch = securityPatch,
            fingerprint = fingerprint,
            screenResolution = screenResolution,
            screenDpi = screenDpi,
            totalRamBytes = totalRamBytes,
            totalStorageBytes = totalStorageBytes,
            hardwareCallsign = hardwareCallsign
        )
    }
}

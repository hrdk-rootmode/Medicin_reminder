package com.example.medicinreminder.security

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.File

object DeviceIntegrityChecker {
    private val suspiciousBinaryPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/system/app/Superuser.apk",
        "/system/app/Magisk.apk",
        "/data/adb/magisk",
        "/data/adb/modules",
        "/system/xbin/daemonsu"
    )

    fun isDeviceCompromised(context: Context): Boolean {
        if (Debug.isDebuggerConnected()) return true
        if (Build.TAGS?.contains("test-keys", ignoreCase = true) == true) return true

        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        val emulatorIndicators = listOf(
            fingerprint.contains("generic"),
            fingerprint.contains("unknown"),
            model.contains("google_sdk"),
            model.contains("emulator"),
            model.contains("android sdk built for x86"),
            brand.startsWith("generic"),
            device.startsWith("generic"),
            product.contains("sdk") || product.contains("emulator"),
            hardware.contains("goldfish") || hardware.contains("ranchu")
        )
        if (emulatorIndicators.any { it }) return true

        if (suspiciousBinaryPaths.any { File(it).exists() }) return true

        // If common system commands can find su, consider the device compromised.
        val suCheck = runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "which su"))
            process.inputStream.bufferedReader().use { it.readText() }.trim()
        }.getOrDefault("")
        if (suCheck.isNotBlank()) return true

        // Basic tamper indicator: debug build on a release-like environment is suspicious.
        if (BuildConfigLike.isDebuggable(context)) return true

        return false
    }
}

private object BuildConfigLike {
    fun isDebuggable(context: Context): Boolean {
        return try {
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Exception) {
            false
        }
    }
}

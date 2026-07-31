package com.rynime.nvplayer.rife

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Gates which RIFE modes get offered to the user. This is a HEURISTIC, not a
 * benchmark - it exists so the UI can fail closed on obviously-unsupported
 * hardware instead of letting someone start a real-time job that will just
 * thermal-throttle or stutter badly.
 *
 * The RAM/API-level thresholds below are a starting point calibrated against
 * SVPlayer's own published requirement (Android 9+, 3GB+ RAM, Snapdragon
 * 865-class recommended for real-time MEMC) - see the "Fase 1" section of
 * RIFE_INTEGRATION_README.md. Replace isLikelyFlagshipTier()'s heuristic with
 * real device-farm benchmark data once that phase has run; do not ship this
 * heuristic as-is to production without validating it against actual
 * interpolate() latency on a handful of physical mid-range devices first.
 */
object RifeCapabilityProbe {

    fun probe(context: Context): RealtimeAvailability {
        val pm = context.packageManager
        val vulkanVersion = vulkanApiVersion(pm)
            ?: return RealtimeAvailability.Unavailable("No Vulkan hardware level reported by this device")

        if (vulkanVersion < VULKAN_1_1) {
            return RealtimeAvailability.BatchOnly(
                "Vulkan $vulkanVersion present but below 1.1 - real-time RIFE needs 1.1+, " +
                    "export/batch mode still works via CPU fallback"
            )
        }

        val ramGb = totalRamGb(context)
        if (ramGb < MIN_RAM_GB_REALTIME) {
            return RealtimeAvailability.BatchOnly(
                "Only ${"%.1f".format(ramGb)}GB RAM - below the ${MIN_RAM_GB_REALTIME}GB floor " +
                    "for sustained real-time interpolation"
            )
        }

        return RealtimeAvailability.RealtimeCapable(vulkanVersion)
    }

    private fun vulkanApiVersion(pm: PackageManager): Int? {
        if (!pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) return null
        // FEATURE_VULKAN_HARDWARE_VERSION's reported version encodes major/minor
        // in the same VK_MAKE_VERSION scheme Vulkan itself uses.
        val info = pm.getSystemAvailableFeatures().firstOrNull {
            it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
        } ?: return null
        return info.version
    }

    private fun totalRamGb(context: Context): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 0.0
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    }

    private const val VULKAN_1_1 = 0x00401000 // VK_MAKE_VERSION(1, 1, 0)
    private const val MIN_RAM_GB_REALTIME = 4.0
}

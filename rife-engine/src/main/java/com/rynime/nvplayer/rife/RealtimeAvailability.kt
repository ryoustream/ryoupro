package com.rynime.nvplayer.rife

/**
 * Result of [RifeCapabilityProbe.probe]. Deliberately three-valued rather
 * than boolean: SVPlayer's own published requirement (Android 9+, Snapdragon
 * 865-class+) is the real-world calibration point for [RealtimeCapable] -
 * most devices below that line should still get [BatchOnly] rather than
 * nothing.
 */
sealed class RealtimeAvailability {
    /** Vulkan present and the device clears the flagship-tier heuristic. */
    data class RealtimeCapable(val vulkanApiVersion: Int) : RealtimeAvailability()

    /** Vulkan present but device tier heuristic says real-time is unrealistic. */
    data class BatchOnly(val reason: String) : RealtimeAvailability()

    /** No usable Vulkan device at all - RIFE falls back to CPU (Mode A only, slow). */
    data class Unavailable(val reason: String) : RealtimeAvailability()
}

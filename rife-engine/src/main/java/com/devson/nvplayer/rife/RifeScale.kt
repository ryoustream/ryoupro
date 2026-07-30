package com.devson.nvplayer.rife

/**
 * Output frame-rate multiplier. 3x/4x need a model with
 * [RifeModel.supportsCustomTimestep] = true since they require timesteps
 * other than the fixed 0.5 midpoint (e.g. 4x = 0.25, 0.5, 0.75).
 */
enum class RifeScale(val factor: Int, val displayName: String) {
    X2(2, "2x (e.g. 30fps -> 60fps)"),
    X3(3, "3x"),
    X4(4, "4x");

    /** Timesteps needed to produce (factor - 1) interpolated frames between each pair. */
    fun timesteps(): List<Float> = (1 until factor).map { it.toFloat() / factor }
}

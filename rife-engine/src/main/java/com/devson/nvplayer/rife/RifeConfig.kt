package com.devson.nvplayer.rife

/**
 * The "full API" surface for RIFE - every knob rife-ncnn-vulkan exposes,
 * not a single hardcoded model+setting. Passed into [RifeInterpolator.init].
 */
data class RifeConfig(
    val model: RifeModel = RifeModel.default,
    val scale: RifeScale = RifeScale.X2,
    val ttaSpatial: Boolean = false,
    val ttaTemporal: Boolean = false,
    /** For >=2K sources - tiled inference to bound VRAM use. Slower. */
    val uhdMode: Boolean = false,
    /** -1 = auto-pick fastest Vulkan device, -2 = force CPU fallback. */
    val gpuId: Int = -1,
    val numThreads: Int = 2,
) {
    init {
        require(scale == RifeScale.X2 || model.supportsCustomTimestep) {
            "${model.displayName} only supports 2x (fixed 0.5 timestep); " +
                "pick RifeModel.V4_6 or V4_25 for ${scale.displayName}"
        }
    }
}

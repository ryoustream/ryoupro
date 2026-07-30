package com.devson.nvplayer.rife

/**
 * Which RIFE checkpoint to run. Mirrors the enum(value, displayName) style
 * already used by [com.devson.nvplayer.player.model.DecoderMode].
 *
 * assetFolder must match a directory placed under
 * app/src/main/assets/rife_models/ - see rife-engine's README_NATIVE_SETUP.md.
 * Models are NOT bundled in this patch (15-25MB each); ship at least one
 * (V4_6 recommended - best speed/quality balance for mobile) via an on-demand
 * asset pack rather than the base APK if you add more than one, see
 * RIFE_INTEGRATION_README.md "APK size" section.
 */
enum class RifeModel(val assetFolder: String, val displayName: String, val supportsCustomTimestep: Boolean) {
    V4_6("rife-v4.6", "RIFE v4.6 (Balanced)", supportsCustomTimestep = true),
    V4_25("rife-v4.25", "RIFE v4.25 (Higher quality, slower)", supportsCustomTimestep = true),
    ANIME("rife-anime", "RIFE Anime (optimized for cel animation)", supportsCustomTimestep = false);

    companion object {
        val default: RifeModel get() = V4_6
    }
}

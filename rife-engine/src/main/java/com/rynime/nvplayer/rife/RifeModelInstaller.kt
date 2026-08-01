package com.rynime.nvplayer.rife

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extracts a bundled RIFE model from app assets (packaged read-only inside
 * the APK) to internal storage (a real filesystem path, which is what the
 * native RIFE::load() needs - ncnn's Vulkan model loader reads .param/.bin
 * files directly off disk, not from an AssetManager stream).
 */
object RifeModelInstaller {

    /** Idempotent: skips extraction if the model directory already exists with content. */
    suspend fun ensureExtracted(context: Context, model: RifeModel): File =
        withContext(Dispatchers.IO) {
            val destDir = File(context.filesDir, "rife_models/${model.assetFolder}")
            val assetDir = "rife_models/${model.assetFolder}"

            val assetFiles = context.assets.list(assetDir)
            if (assetFiles.isNullOrEmpty()) {
                throw IllegalStateException(
                    "No bundled assets found at assets/$assetDir - model wasn't packaged into " +
                        "this APK. See rife-engine/src/main/cpp/README_NATIVE_SETUP.md."
                )
            }

            val alreadyExtracted = assetFiles.all { File(destDir, it).exists() }
            if (alreadyExtracted) return@withContext destDir

            destDir.mkdirs()
            for (fileName in assetFiles) {
                context.assets.open("$assetDir/$fileName").use { input ->
                    File(destDir, fileName).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            destDir
        }
}

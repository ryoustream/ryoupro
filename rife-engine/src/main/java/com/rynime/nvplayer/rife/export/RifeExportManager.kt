package com.rynime.nvplayer.rife.export

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rynime.nvplayer.rife.RifeScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Facade the app module's ViewModel talks to - keeps WorkManager/WorkInfo
 * details out of ui/screen code, matching how MPVPlayerEngine hides
 * MPVLib's raw event model behind StateFlow properties.
 *
 * Uses a single UNIQUE work name rather than tracking a per-call UUID -
 * two things this fixes that a UUID-based approach doesn't:
 * 1. enqueueUniqueWork(..., KEEP, ...) means tapping "Start export" again
 *    while one is already running is a safe no-op instead of starting a
 *    second concurrent job racing the first one for the GPU/output file
 *    (this was very likely the actual cause of a "corrupted" export - not
 *    a second bug in the encode pipeline itself, but two RifeExportWorkers
 *    genuinely running at once).
 * 2. observeCurrent() can be collected immediately by a BRAND NEW
 *    ViewModel/screen instance (e.g. after navigating away and back, which
 *    recreates the ViewModel) and will immediately pick up whatever's
 *    already running - no UUID needs to survive across that recreation.
 */
class RifeExportManager(private val context: Context) {

    /** [inputUriString] should be a content:// URI string, e.g. from ActivityResultContracts.GetContent(). */
    fun enqueueExport(inputUriString: String, outputPath: String, scale: RifeScale) {
        val request = OneTimeWorkRequestBuilder<RifeExportWorker>()
            .setInputData(
                workDataOf(
                    RifeExportWorker.KEY_INPUT_PATH to inputUriString,
                    RifeExportWorker.KEY_OUTPUT_PATH to outputPath,
                    RifeExportWorker.KEY_SCALE_FACTOR to scale.factor,
                )
            )
            .addTag(TAG_EXPORT)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Reflects whatever's currently enqueued/running/most-recently-finished
     * under the unique work name - safe to collect from a freshly created
     * ViewModel with no prior knowledge of an in-flight job.
     */
    fun observeCurrent(): Flow<ExportProgress?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
            .map { infos -> infos.firstOrNull()?.toExportProgress() }

    fun cancelCurrent() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun WorkInfo.toExportProgress(): ExportProgress = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportProgress.Queued
        WorkInfo.State.RUNNING -> {
            val done = progress.getInt("framesDone", -1)
            val total = progress.getInt("framesTotal", -1)
            val eta = progress.getInt("etaSeconds", -1).takeIf { it >= 0 }
            if (done >= 0 && total > 0) {
                ExportProgress.Running(done, total, etaSeconds = eta)
            } else {
                ExportProgress.Running(0, 0, etaSeconds = null)
            }
        }
        WorkInfo.State.SUCCEEDED ->
            ExportProgress.Done(outputData.getString(RifeExportWorker.KEY_OUTPUT_PATH) ?: "")
        WorkInfo.State.FAILED ->
            ExportProgress.Failed(outputData.getString("error") ?: "Unknown failure")
        WorkInfo.State.CANCELLED -> ExportProgress.Failed("Cancelled")
    }

    companion object {
        private const val TAG_EXPORT = "rife_export"
        private const val UNIQUE_WORK_NAME = "rife_export_unique"
    }
}

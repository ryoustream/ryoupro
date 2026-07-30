package com.devson.nvplayer.rife.export

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.devson.nvplayer.rife.RifeScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Facade the app module's ViewModel talks to - keeps WorkManager/WorkInfo
 * details out of ui/screen code, matching how MPVPlayerEngine hides
 * MPVLib's raw event model behind StateFlow properties.
 */
class RifeExportManager(private val context: Context) {

    /** [inputUriString] should be a content:// URI string, e.g. from ActivityResultContracts.GetContent(). */
    fun enqueueExport(inputUriString: String, outputPath: String, scale: RifeScale): java.util.UUID {
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
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    fun observe(workId: java.util.UUID): Flow<ExportProgress> =
        WorkManager.getInstance(context).getWorkInfoByIdFlow(workId).map { info -> info.toExportProgress() }

    fun cancel(workId: java.util.UUID) {
        WorkManager.getInstance(context).cancelWorkById(workId)
    }

    private fun WorkInfo?.toExportProgress(): ExportProgress = when (this?.state) {
        null, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportProgress.Queued
        WorkInfo.State.RUNNING -> {
            val done = progress.getInt("framesDone", -1)
            val total = progress.getInt("framesTotal", -1)
            if (done >= 0 && total > 0) {
                ExportProgress.Running(done, total, etaSeconds = null)
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
    }
}

package com.devson.nvplayer.rife.export

sealed class ExportProgress {
    data object Queued : ExportProgress()
    data class Running(val framesDone: Int, val framesTotal: Int, val etaSeconds: Int?) : ExportProgress()
    data class Done(val outputPath: String) : ExportProgress()
    data class Failed(val reason: String) : ExportProgress()
}

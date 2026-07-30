package com.devson.nvplayer.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.rife.RealtimeAvailability
import com.devson.nvplayer.rife.RifeCapabilityProbe
import com.devson.nvplayer.rife.RifeScale
import com.devson.nvplayer.rife.export.ExportProgress
import com.devson.nvplayer.rife.export.RifeExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * ViewModel for [com.devson.nvplayer.ui.screen.tools.SmoothMotionExportScreen].
 * Mode A (batch export) only - Mode B (real-time) has no UI surface yet since
 * it depends on the mpv-android-patch prerequisite (see RifeFrameSource.kt).
 */
class SmoothMotionViewModel(application: Application) : AndroidViewModel(application) {

    private val exportManager = RifeExportManager(application.applicationContext)

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _scale = MutableStateFlow(RifeScale.X2)
    val scale: StateFlow<RifeScale> = _scale.asStateFlow()

    private val _capability = MutableStateFlow<RealtimeAvailability?>(null)
    val capability: StateFlow<RealtimeAvailability?> = _capability.asStateFlow()

    private val _progress = MutableStateFlow<ExportProgress?>(null)
    val progress: StateFlow<ExportProgress?> = _progress.asStateFlow()

    private var activeJobId: UUID? = null

    init {
        // Capability probing is cheap (PackageManager feature flags + one
        // ActivityManager call) so it's fine to run eagerly on screen open
        // rather than lazily on first export tap.
        _capability.value = RifeCapabilityProbe.probe(application.applicationContext)
    }

    fun onFileSelected(uri: Uri) {
        _selectedFile.value = uri
    }

    fun onScaleSelected(newScale: RifeScale) {
        _scale.value = newScale
    }

    fun startExport() {
        val input = _selectedFile.value ?: return
        val app = getApplication<Application>()
        val outputDir = File(app.getExternalFilesDir(null), "smooth_exports").apply { mkdirs() }
        val outputFile = File(outputDir, "smooth_${System.currentTimeMillis()}.mp4")

        val jobId = exportManager.enqueueExport(input.toString(), outputFile.absolutePath, _scale.value)
        activeJobId = jobId
        viewModelScope.launch {
            exportManager.observe(jobId).collect { _progress.value = it }
        }
    }

    fun cancelExport() {
        activeJobId?.let { exportManager.cancel(it) }
    }
}

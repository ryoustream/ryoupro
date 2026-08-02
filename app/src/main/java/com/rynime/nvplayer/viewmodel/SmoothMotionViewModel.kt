package com.rynime.nvplayer.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rynime.nvplayer.rife.RealtimeAvailability
import com.rynime.nvplayer.rife.RifeCapabilityProbe
import com.rynime.nvplayer.rife.RifeScale
import com.rynime.nvplayer.rife.export.ExportProgress
import com.rynime.nvplayer.rife.export.RifeExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for [com.rynime.nvplayer.ui.screen.tools.SmoothMotionExportScreen].
 * Mode A (batch export) only - Mode B (real-time) has no UI surface yet since
 * it depends on the mpv-android-patch prerequisite (see RifeFrameSource.kt).
 *
 * Always observes RifeExportManager.observeCurrent() from init{} - this is
 * the fix for a real bug: previously this ViewModel only started observing
 * a job right after IT enqueued one (tracking a UUID in a plain var), so
 * navigating away and back (which recreates the ViewModel) lost all track
 * of an export that was still genuinely running in the background via
 * WorkManager. The screen would show a blank/zero state, look like nothing
 * was happening, and invite exactly what a reasonable person would do -
 * tap "Start export" again - which used to start a SECOND concurrent
 * export job racing the first one for the same GPU/output file. That race
 * is the most likely actual cause of a "corrupted" export result, not a
 * second bug in the encode pipeline itself. RifeExportManager now also
 * uses enqueueUniqueWork(..., KEEP, ...) as a second, independent guard
 * against that scenario even if this observation fix is somehow bypassed.
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

    init {
        // Capability probing is cheap (PackageManager feature flags + one
        // ActivityManager call) so it's fine to run eagerly on screen open
        // rather than lazily on first export tap.
        _capability.value = RifeCapabilityProbe.probe(application.applicationContext)

        // Resume observing whatever's already running (or just finished),
        // regardless of whether THIS ViewModel instance is the one that
        // started it - see the class doc above.
        viewModelScope.launch {
            exportManager.observeCurrent().collect { _progress.value = it }
        }
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
        // enqueueUniqueWork(..., KEEP, ...) inside enqueueExport makes this a
        // safe no-op if a job is already running - no explicit check needed
        // here, though the UI also disables the button while progress is
        // Running/Queued (see SmoothMotionExportScreen) as a first line of
        // defense the user actually sees.
        exportManager.enqueueExport(input.toString(), outputFile.absolutePath, _scale.value)
    }

    fun cancelExport() {
        exportManager.cancelCurrent()
    }
}

package com.rynime.nvplayer.ui.screen.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rynime.nvplayer.rife.RealtimeAvailability
import com.rynime.nvplayer.rife.RifeScale
import com.rynime.nvplayer.rife.export.ExportProgress
import com.rynime.nvplayer.viewmodel.SmoothMotionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmoothMotionExportScreen(
    onBack: () -> Unit,
    viewModel: SmoothMotionViewModel = viewModel(),
) {
    val selectedFile by viewModel.selectedFile.collectAsState()
    val scale by viewModel.scale.collectAsState()
    val capability by viewModel.capability.collectAsState()
    val progress by viewModel.progress.collectAsState()

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::onFileSelected) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smooth Motion (RIFE)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Interpolates a video to a higher frame rate using RIFE (AI-based motion " +
                    "interpolation), the same technique SVP uses on desktop. This exports a new " +
                    "file - it does not change live playback.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CapabilityCard(capability)

            Surface(
                onClick = { filePickerLauncher.launch("video/*") },
                shape = RoundedCornerShape16,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Text(
                        selectedFile?.lastPathSegment ?: "Choose a video…",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Text("Target smoothness", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RifeScale.entries.forEach { option ->
                    FilterChip(
                        selected = scale == option,
                        onClick = { viewModel.onScaleSelected(option) },
                        label = { Text(option.displayName) }
                    )
                }
            }

            when (val p = progress) {
                null -> Unit
                is ExportProgress.Queued -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Queued…", style = MaterialTheme.typography.bodySmall)
                }
                is ExportProgress.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val fraction = if (p.framesTotal > 0) p.framesDone.toFloat() / p.framesTotal else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    val label = if (p.framesTotal > 0) {
                        val etaText = p.etaSeconds?.let { s -> " (~${s / 60}m ${s % 60}s left)" } ?: ""
                        "${p.framesDone} / ${p.framesTotal} frames$etaText"
                    } else {
                        "Loading model + starting GPU… (can take longer on first run)"
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
                is ExportProgress.Done -> Text(
                    "Done - saved to ${p.outputPath}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                is ExportProgress.Failed -> Text(
                    "Export failed: ${p.reason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = viewModel::startExport,
                enabled = selectedFile != null && capability !is RealtimeAvailability.Unavailable &&
                    (progress == null || progress is ExportProgress.Done || progress is ExportProgress.Failed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start export")
            }
        }
    }
}

@Composable
private fun CapabilityCard(capability: RealtimeAvailability?) {
    val (text, color) = when (capability) {
        null -> "Checking device capability…" to MaterialTheme.colorScheme.onSurfaceVariant
        is RealtimeAvailability.RealtimeCapable ->
            "This device meets the flagship-tier heuristic for real-time smoothing " +
                "(Mode B is not shipped yet - export/batch mode below works today)." to MaterialTheme.colorScheme.primary
        is RealtimeAvailability.BatchOnly ->
            "${capability.reason}. Batch export below will still work, just not in real time." to MaterialTheme.colorScheme.onSurfaceVariant
        is RealtimeAvailability.Unavailable ->
            "${capability.reason}. RIFE needs a Vulkan-capable GPU." to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape16,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

private val RoundedCornerShape16 = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

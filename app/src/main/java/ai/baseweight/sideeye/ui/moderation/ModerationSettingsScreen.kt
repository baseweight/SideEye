package ai.baseweight.sideeye.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.baseweight.sideeye.data.ai.FlagCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationSettingsScreen(
    viewModel: ModerationViewModel,
    onBackClick: () -> Unit,
    onStartScan: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Scan Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model Status Card
            ModelStatusCard(
                isDownloaded = uiState.isModelDownloaded,
                isDownloading = uiState.isDownloading,
                downloadProgress = uiState.downloadProgress,
                downloadStatus = uiState.downloadStatus,
                isModelLoading = uiState.isModelLoading,
                isModelReady = uiState.isModelReady,
                onDownloadClick = { viewModel.downloadModel() },
                onLoadClick = { viewModel.initializeModel() }
            )

            // Categories Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Categories to Flag",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select which types of content to detect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    FlagCategory.entries.forEach { category ->
                        CategoryToggle(
                            category = category,
                            isEnabled = category in uiState.settings.enabledCategories,
                            onToggle = { viewModel.toggleCategory(category) }
                        )
                    }
                }
            }

            // Sensitivity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Detection Sensitivity",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Higher values require more confidence to flag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sensitive", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${(uiState.settings.sensitivityThreshold * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text("Strict", style = MaterialTheme.typography.labelSmall)
                    }

                    Slider(
                        value = uiState.settings.sensitivityThreshold,
                        onValueChange = { viewModel.updateSensitivity(it) },
                        valueRange = 0.5f..0.95f,
                        steps = 8
                    )
                }
            }

            // Scan Progress Card (shown during scan)
            if (uiState.isScanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Scanning Photos...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = uiState.scanProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.scannedCount} / ${uiState.totalToScan} photos",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${uiState.flaggedQueue.size} flagged",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start Scan Button
            Button(
                onClick = {
                    if (uiState.isModelReady) {
                        viewModel.startScan()
                        // Navigate to moderation screen when scan starts
                        onStartScan()
                    } else if (uiState.isModelDownloaded && !uiState.isModelLoading) {
                        viewModel.initializeModel()
                    } else if (!uiState.isModelDownloaded && !uiState.isDownloading) {
                        viewModel.downloadModel()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isDownloading && !uiState.isModelLoading && !uiState.isScanning
            ) {
                if (uiState.isDownloading || uiState.isModelLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (uiState.isModelReady) Icons.Default.PlayArrow else Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = when {
                        !uiState.isModelDownloaded -> "Download AI Model"
                        !uiState.isModelReady -> "Load AI Model"
                        else -> "Start Smart Scan"
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelStatusCard(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadStatus: String,
    isModelLoading: Boolean,
    isModelReady: Boolean,
    onDownloadClick: () -> Unit,
    onLoadClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isModelReady -> MaterialTheme.colorScheme.primaryContainer
                isDownloaded -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "AI Model Status",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            when {
                isModelReady -> {
                    Text(
                        text = "Ready to scan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                isModelLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 12.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Loading model...")
                    }
                }
                isDownloading -> {
                    Column {
                        Text(downloadStatus, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                isDownloaded -> {
                    Text(
                        text = "Model downloaded. Tap 'Load AI Model' to prepare.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    Text(
                        text = "AI model not downloaded (~4.5GB required)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryToggle(
    category: FlagCategory,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() }
        )
    }
}

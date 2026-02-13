package ai.baseweight.sideeye.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ModelDownloadScreen(
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadError: String?,
    isDownloaded: Boolean,
    isOnWifi: Boolean,
    onDownload: () -> Unit,
    onContinue: () -> Unit
) {
    // Auto-start download when on WiFi
    LaunchedEffect(isOnWifi) {
        if (!isDownloaded && !isDownloading && isOnWifi) {
            onDownload()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            isDownloaded -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            downloadError != null -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when {
                isDownloaded -> "Model Ready"
                isDownloading -> "Downloading AI Model..."
                downloadError != null -> "Download Failed"
                !isOnWifi -> "Waiting for WiFi"
                else -> "Download AI Model"
            },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                isDownloaded -> "The OmniNeural AI model is ready. All photo analysis happens on your device - nothing is uploaded."
                isDownloading -> "Download will continue in the background. You can proceed with setup."
                downloadError != null -> downloadError
                !isOnWifi -> "The AI model is ~2GB. Connect to WiFi to start downloading, or tap below to download anyway."
                else -> "SideEye uses a 2GB AI model that runs entirely on your device."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = if (downloadError != null)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isDownloading) {
            LinearProgressIndicator(
                progress = downloadProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${(downloadProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "OmniNeural-4B",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "4 billion parameter vision-language model optimized for Qualcomm Snapdragon NPU",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when {
            isDownloaded -> {
                // Model ready - just continue
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Continue")
                }
            }
            isDownloading -> {
                // Downloading - allow continuing while download runs in background
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Continue Setup")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Download will continue in background",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            downloadError != null -> {
                // Error - retry button
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Retry Download")
                }
            }
            !isOnWifi -> {
                // Not on WiFi - option to download anyway
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Download on Mobile Data")
                }
            }
            else -> {
                // Waiting to start (shouldn't happen with auto-start)
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Start Download")
                }
            }
        }
    }
}

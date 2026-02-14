package ai.baseweight.sideeye.ui.vault

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.baseweight.sideeye.data.vault.VaultImage
import androidx.compose.foundation.Image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel = viewModel(),
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    // Reload vault images when screen appears
    LaunchedEffect(Unit) {
        viewModel.loadVaultImages()
    }

    // Update session activity on lifecycle events
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateSessionActivity()
                viewModel.loadVaultImages()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text("${uiState.selectedImages.size} selected")
                    } else {
                        Text("Private Vault")
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.clearSelection()
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            if (uiState.isSelectionMode) Icons.Default.Close
                            else Icons.Filled.ArrowBack,
                            contentDescription = if (uiState.isSelectionMode) "Clear selection" else "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        // Restore to gallery
                        IconButton(onClick = { showRestoreDialog = true }) {
                            Icon(Icons.Filled.Image, contentDescription = "Restore to Gallery")
                        }
                        // Delete
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    } else {
                        // Lock vault
                        IconButton(onClick = { viewModel.lockVault(); onBackClick() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault")
                        }
                        // Menu
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = {
                                        showMenu = false
                                        onSettingsClick()
                                    }
                                )
                                if (uiState.images.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Select All") },
                                        onClick = {
                                            showMenu = false
                                            viewModel.selectAllImages()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }
            uiState.images.isEmpty() -> {
                EmptyVaultContent(modifier = Modifier.padding(paddingValues))
            }
            else -> {
                VaultImageGrid(
                    images = uiState.images,
                    selectedImages = uiState.selectedImages,
                    isSelectionMode = uiState.isSelectionMode,
                    onImageClick = { image ->
                        if (uiState.isSelectionMode) {
                            viewModel.toggleImageSelection(image.id)
                        }
                        viewModel.updateSessionActivity()
                    },
                    onImageLongClick = { image ->
                        viewModel.toggleImageSelection(image.id)
                        viewModel.updateSessionActivity()
                    },
                    getDecryptedStream = { id -> viewModel.getDecryptedImageStream(id) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Images?") },
            text = {
                Text(
                    "Are you sure you want to permanently delete ${uiState.selectedImages.size} " +
                    "image${if (uiState.selectedImages.size > 1) "s" else ""}? This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedImages()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restore confirmation dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore to Gallery?") },
            text = {
                Text(
                    "Restore ${uiState.selectedImages.size} image${if (uiState.selectedImages.size > 1) "s" else ""} " +
                    "to your photo gallery? They will be removed from the vault."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreSelectedImages()
                        showRestoreDialog = false
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = "Loading vault...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun EmptyVaultContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Your private vault is empty",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Long-press images in your gallery and select \"Move to Vault\" to hide them here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultImageGrid(
    images: List<VaultImage>,
    selectedImages: Set<String>,
    isSelectionMode: Boolean,
    onImageClick: (VaultImage) -> Unit,
    onImageLongClick: (VaultImage) -> Unit,
    getDecryptedStream: (String) -> java.io.InputStream?,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(images, key = { it.id }) { image ->
            VaultImageGridItem(
                image = image,
                isSelected = image.id in selectedImages,
                isSelectionMode = isSelectionMode,
                onClick = { onImageClick(image) },
                onLongClick = { onImageLongClick(image) },
                getDecryptedStream = getDecryptedStream
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultImageGridItem(
    image: VaultImage,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    getDecryptedStream: (String) -> java.io.InputStream?
) {
    // Load and cache the decrypted bitmap
    val imageBitmap = remember(image.id) {
        try {
            getDecryptedStream(image.id)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = image.originalName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder for loading or error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Selection overlay
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.scrim.copy(alpha = 0.1f)
                    )
            )
            // Selection indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

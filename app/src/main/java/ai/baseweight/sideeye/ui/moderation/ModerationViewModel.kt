package ai.baseweight.sideeye.ui.moderation

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.baseweight.sideeye.data.GalleryImage
import ai.baseweight.sideeye.data.OnboardingManager
import ai.baseweight.sideeye.data.ai.AnalysisResult
import ai.baseweight.sideeye.data.ai.FlagCategory
import ai.baseweight.sideeye.data.ai.ImageAnalyzer
import ai.baseweight.sideeye.data.ai.ModelDownloader
import ai.baseweight.sideeye.data.ai.ModerationSettings
import ai.baseweight.sideeye.data.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationUiState(
    // Settings
    val settings: ModerationSettings = ModerationSettings.getDefault(),

    // Model state
    val isModelDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadStatus: String = "",
    val isModelLoading: Boolean = false,
    val isModelReady: Boolean = false,

    // Scanning state
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val scannedCount: Int = 0,
    val totalToScan: Int = 0,

    // Moderation queue
    val flaggedQueue: List<Pair<GalleryImage, AnalysisResult>> = emptyList(),
    val currentIndex: Int = 0,
    val processedCount: Int = 0,

    // Actions
    val isProcessingAction: Boolean = false,
    val lastActionError: String? = null
) {
    val currentItem: Pair<GalleryImage, AnalysisResult>?
        get() = flaggedQueue.getOrNull(currentIndex)

    val remainingCount: Int
        get() = (flaggedQueue.size - currentIndex).coerceAtLeast(0)

    val hasMoreItems: Boolean
        get() = currentIndex < flaggedQueue.size
}

class ModerationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ModerationViewModel"
    }

    private val _uiState = MutableStateFlow(ModerationUiState())
    val uiState: StateFlow<ModerationUiState> = _uiState.asStateFlow()

    private val downloader = ModelDownloader(application)
    private val analyzer = ImageAnalyzer(application)
    private val vaultRepository = VaultRepository(application)
    private val onboardingManager = OnboardingManager(application)

    init {
        loadSettings()
        checkModelStatus()
    }

    private fun loadSettings() {
        val settings = ModerationSettings.load(getApplication())
        _uiState.update { it.copy(settings = settings) }
    }

    fun updateSettings(settings: ModerationSettings) {
        ModerationSettings.save(getApplication(), settings)
        _uiState.update { it.copy(settings = settings) }
    }

    fun toggleCategory(category: FlagCategory) {
        val current = _uiState.value.settings
        val newCategories = if (category in current.enabledCategories) {
            current.enabledCategories - category
        } else {
            current.enabledCategories + category
        }
        updateSettings(current.copy(enabledCategories = newCategories))
    }

    fun updateEnabledCategories(categories: Set<FlagCategory>) {
        val current = _uiState.value.settings
        updateSettings(current.copy(enabledCategories = categories))
    }

    fun updateSensitivity(threshold: Float) {
        val current = _uiState.value.settings
        updateSettings(current.copy(sensitivityThreshold = threshold))
    }

    private fun checkModelStatus() {
        val isDownloaded = downloader.isModelDownloaded(ModelDownloader.OMNINEURAL_MODEL_ID)
        _uiState.update { it.copy(isModelDownloaded = isDownloaded) }
    }

    fun downloadModel() {
        if (_uiState.value.isDownloading) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f) }

            downloader.downloadOmniNeural().collect { state ->
                when (state) {
                    is ModelDownloader.DownloadState.Idle -> {
                        _uiState.update { it.copy(downloadStatus = "Preparing...") }
                    }
                    is ModelDownloader.DownloadState.ListingFiles -> {
                        _uiState.update { it.copy(downloadStatus = "Finding files...") }
                    }
                    is ModelDownloader.DownloadState.Downloading -> {
                        val progress = if (state.totalBytes > 0) {
                            state.bytesDownloaded.toFloat() / state.totalBytes
                        } else 0f
                        val mb = state.bytesDownloaded / (1024 * 1024)
                        _uiState.update {
                            it.copy(
                                downloadProgress = progress,
                                downloadStatus = "Downloading ${state.currentFile} (${mb}MB)"
                            )
                        }
                    }
                    is ModelDownloader.DownloadState.Completed -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                isModelDownloaded = true,
                                downloadProgress = 1f,
                                downloadStatus = "Download complete"
                            )
                        }
                    }
                    is ModelDownloader.DownloadState.Error -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadStatus = "Error: ${state.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun initializeModel() {
        if (_uiState.value.isModelLoading || _uiState.value.isModelReady) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isModelLoading = true) }

            val success = analyzer.initialize()

            _uiState.update {
                it.copy(
                    isModelLoading = false,
                    isModelReady = success
                )
            }
        }
    }

    /**
     * Start scanning images. If scanNewOnly is true, only scans images added since last scan.
     */
    fun startScan(scanNewOnly: Boolean = true) {
        if (_uiState.value.isScanning || !_uiState.value.isModelReady) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    scanProgress = 0f,
                    scannedCount = 0,
                    flaggedQueue = emptyList(),
                    currentIndex = 0,
                    processedCount = 0
                )
            }

            val lastScanTimestamp = if (scanNewOnly) {
                onboardingManager.getLastScanTimestamp()
            } else {
                0L
            }

            val images = queryImages(sinceTimestamp = lastScanTimestamp)
            _uiState.update { it.copy(totalToScan = images.size) }

            if (images.isEmpty()) {
                _uiState.update { it.copy(isScanning = false) }
                return@launch
            }

            val settings = _uiState.value.settings
            val flagged = mutableListOf<Pair<GalleryImage, AnalysisResult>>()

            images.forEachIndexed { index, image ->
                try {
                    val result = analyzer.analyze(
                        imageUri = image.uri,
                        imageId = image.id,
                        enabledCategories = settings.enabledCategories,
                        threshold = settings.sensitivityThreshold
                    )

                    if (result.isFlagged) {
                        flagged.add(image to result)
                    }

                    _uiState.update {
                        it.copy(
                            scannedCount = index + 1,
                            scanProgress = (index + 1).toFloat() / images.size,
                            flaggedQueue = flagged.toList()
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to analyze image ${image.id}", e)
                }
            }

            // Update last scan timestamp after successful scan
            onboardingManager.updateLastScanTimestamp()

            _uiState.update { it.copy(isScanning = false) }
        }
    }

    fun getLastScanTimestamp(): Long = onboardingManager.getLastScanTimestamp()

    /**
     * Query images from MediaStore, optionally filtering to those added after a timestamp.
     * @param sinceTimestamp Only return images added after this timestamp (seconds since epoch). 0 means all images.
     */
    private fun queryImages(sinceTimestamp: Long = 0L): List<GalleryImage> {
        val images = mutableListOf<GalleryImage>()
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )

        // Filter by timestamp if provided (DATE_ADDED is in seconds)
        val selection = if (sinceTimestamp > 0L) {
            "${MediaStore.Images.Media.DATE_ADDED} > ?"
        } else null

        val selectionArgs = if (sinceTimestamp > 0L) {
            // Convert from millis to seconds for MediaStore
            arrayOf((sinceTimestamp / 1000).toString())
        } else null

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val date = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)

                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                images.add(
                    GalleryImage(
                        id = id,
                        uri = uri,
                        displayName = name,
                        dateAdded = date,
                        size = size
                    )
                )
            }
        }

        return images
    }

    fun swipeLeft() {
        // Delete the current image
        val current = _uiState.value.currentItem ?: return
        if (_uiState.value.isProcessingAction) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessingAction = true, lastActionError = null) }

            try {
                val context = getApplication<Application>()
                val deleted = context.contentResolver.delete(current.first.uri, null, null)

                if (deleted > 0) {
                    moveToNext()
                } else {
                    _uiState.update { it.copy(lastActionError = "Failed to delete image") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image", e)
                _uiState.update { it.copy(lastActionError = "Error: ${e.message}") }
            }

            _uiState.update { it.copy(isProcessingAction = false) }
        }
    }

    fun swipeRight() {
        // Keep the image, just move to next
        moveToNext()
    }

    fun swipeUp() {
        // Move to vault
        val current = _uiState.value.currentItem ?: return
        if (_uiState.value.isProcessingAction) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessingAction = true, lastActionError = null) }

            try {
                val context = getApplication<Application>()
                val vaultImage = vaultRepository.addToVault(
                    current.first.uri,
                    current.first.displayName
                )

                if (vaultImage != null) {
                    // Delete from gallery after successful vault addition
                    context.contentResolver.delete(current.first.uri, null, null)
                    moveToNext()
                } else {
                    _uiState.update { it.copy(lastActionError = "Failed to move to vault") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to move to vault", e)
                _uiState.update { it.copy(lastActionError = "Error: ${e.message}") }
            }

            _uiState.update { it.copy(isProcessingAction = false) }
        }
    }

    private fun moveToNext() {
        _uiState.update {
            it.copy(
                currentIndex = it.currentIndex + 1,
                processedCount = it.processedCount + 1
            )
        }
    }

    fun resetQueue() {
        _uiState.update {
            it.copy(
                flaggedQueue = emptyList(),
                currentIndex = 0,
                processedCount = 0
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            analyzer.release()
        }
    }
}

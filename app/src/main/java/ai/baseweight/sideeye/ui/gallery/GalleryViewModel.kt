package ai.baseweight.sideeye.ui.gallery

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.baseweight.sideeye.data.GalleryImage
import ai.baseweight.sideeye.data.GooglePhotosHelper
import ai.baseweight.sideeye.data.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val images: List<GalleryImage> = emptyList(),
    val isLoading: Boolean = true,
    val hasPermission: Boolean = false,
    val showGooglePhotosWarning: Boolean = false,
    val selectedImage: GalleryImage? = null,
    val selectedImages: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isMovingToVault: Boolean = false
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val vaultRepository = VaultRepository(application)

    init {
        checkGooglePhotosWarning()
    }

    private fun checkGooglePhotosWarning() {
        val context = getApplication<Application>()
        val isInstalled = GooglePhotosHelper.isGooglePhotosInstalled(context)
        val isDismissed = GooglePhotosHelper.isBannerDismissed(context)
        _uiState.value = _uiState.value.copy(showGooglePhotosWarning = isInstalled && !isDismissed)
    }

    fun dismissGooglePhotosWarning() {
        GooglePhotosHelper.dismissBanner(getApplication())
        _uiState.value = _uiState.value.copy(showGooglePhotosWarning = false)
    }

    fun onPermissionGranted() {
        _uiState.value = _uiState.value.copy(hasPermission = true)
        loadImages()
    }

    fun onPermissionDenied() {
        _uiState.value = _uiState.value.copy(hasPermission = false, isLoading = false)
    }

    fun loadImages() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            val images = queryImages()

            _uiState.update {
                it.copy(
                    images = images,
                    isLoading = false
                )
            }
        }
    }

    private fun queryImages(): List<GalleryImage> {
        val images = mutableListOf<GalleryImage>()
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
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

    fun selectImage(image: GalleryImage?) {
        _uiState.value = _uiState.value.copy(selectedImage = image)
    }

    fun openGooglePhotosSettings() {
        GooglePhotosHelper.openGooglePhotosBackupSettings(getApplication())
    }

    // Selection mode methods
    fun toggleImageSelection(imageId: Long) {
        _uiState.update { state ->
            val newSelection = if (imageId in state.selectedImages) {
                state.selectedImages - imageId
            } else {
                state.selectedImages + imageId
            }
            state.copy(
                selectedImages = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
    }

    fun selectAllImages() {
        _uiState.update { state ->
            state.copy(
                selectedImages = state.images.map { it.id }.toSet(),
                isSelectionMode = true
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedImages = emptySet(),
                isSelectionMode = false
            )
        }
    }

    fun moveSelectedToVault() {
        val state = _uiState.value
        val selectedIds = state.selectedImages
        val imagesToMove = state.images.filter { it.id in selectedIds }

        if (imagesToMove.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isMovingToVault = true) }

            val context = getApplication<Application>()

            imagesToMove.forEach { image ->
                // Add to vault (encrypts and copies)
                val vaultImage = vaultRepository.addToVault(image.uri, image.displayName)

                if (vaultImage != null) {
                    // Delete from MediaStore after successful vault addition
                    try {
                        context.contentResolver.delete(image.uri, null, null)
                    } catch (e: Exception) {
                        // If delete fails, the image will still be in the vault
                        // but also remain in the gallery. User can manually delete.
                        e.printStackTrace()
                    }
                }
            }

            // Clear selection and reload images
            _uiState.update {
                it.copy(
                    selectedImages = emptySet(),
                    isSelectionMode = false,
                    isMovingToVault = false
                )
            }

            // Reload gallery to reflect changes
            loadImages()
        }
    }

    fun getSelectedCount(): Int = _uiState.value.selectedImages.size

    fun deleteImage(image: GalleryImage) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                context.contentResolver.delete(image.uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            loadImages()
        }
    }

    fun moveImageToVault(image: GalleryImage, onDeleteRequired: ((Uri) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val vaultImage = vaultRepository.addToVault(image.uri, image.displayName)
            if (vaultImage != null && onDeleteRequired != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDeleteRequired(image.uri)
                }
            } else if (vaultImage != null) {
                val context = getApplication<Application>()
                try {
                    context.contentResolver.delete(image.uri, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                loadImages()
            }
        }
    }
}

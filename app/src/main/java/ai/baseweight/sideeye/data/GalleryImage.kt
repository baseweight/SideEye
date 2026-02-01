package ai.baseweight.sideeye.data

import android.net.Uri

data class GalleryImage(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val size: Long
)

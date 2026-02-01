package ai.baseweight.sideeye.data.vault

import android.net.Uri

/**
 * Represents an image stored in the encrypted private vault.
 */
data class VaultImage(
    val id: String,              // UUID for the vault image
    val originalName: String,    // Original filename
    val encryptedPath: String,   // Path to encrypted file in vault directory
    val dateAdded: Long,         // Timestamp when added to vault
    val size: Long,              // File size in bytes
    val mimeType: String = "image/*"  // MIME type of the image
)

/**
 * Extension to get the encrypted file as a Uri.
 */
fun VaultImage.toUri(): Uri = Uri.parse("file://$encryptedPath")

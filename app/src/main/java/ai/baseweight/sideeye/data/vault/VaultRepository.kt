package ai.baseweight.sideeye.data.vault

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.security.crypto.EncryptedFile
import ai.baseweight.sideeye.data.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Repository for managing encrypted vault storage.
 * Handles encryption, decryption, and metadata management for vault images.
 */
class VaultRepository(private val context: Context) {

    private val vaultDir: File by lazy {
        File(context.filesDir, VAULT_DIRECTORY).apply {
            if (!exists()) mkdirs()
        }
    }

    private val prefs by lazy { SecurityManager.getEncryptedPrefs(context) }

    companion object {
        private const val VAULT_DIRECTORY = "vault"
        private const val KEY_VAULT_IMAGES = "vault_images_metadata"
    }

    /**
     * Add an image to the vault by encrypting and copying it.
     * @param uri URI of the source image
     * @param displayName Original filename
     * @return The VaultImage if successful, null otherwise
     */
    suspend fun addToVault(uri: Uri, displayName: String): VaultImage? = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val extension = getExtensionFromUri(uri) ?: "jpg"
            val encryptedFileName = "$id.$extension.enc"
            val encryptedFile = File(vaultDir, encryptedFileName)

            // Get file size
            val size = context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L

            // Get MIME type
            val mimeType = context.contentResolver.getType(uri) ?: "image/*"

            // Encrypt and copy the file
            val masterKey = SecurityManager.getMasterKey(context)
            val encrypted = EncryptedFile.Builder(
                context,
                encryptedFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            context.contentResolver.openInputStream(uri)?.use { input ->
                encrypted.openFileOutput().use { output ->
                    input.copyTo(output)
                }
            }

            val vaultImage = VaultImage(
                id = id,
                originalName = displayName,
                encryptedPath = encryptedFile.absolutePath,
                dateAdded = System.currentTimeMillis(),
                size = size,
                mimeType = mimeType
            )

            // Save metadata
            saveImageMetadata(vaultImage)

            vaultImage
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Remove an image from the vault.
     * @param id The vault image ID to remove
     * @return true if removal was successful
     */
    suspend fun removeFromVault(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val images = loadImageMetadata().toMutableList()
            val image = images.find { it.id == id } ?: return@withContext false

            // Delete encrypted file
            File(image.encryptedPath).delete()

            // Remove from metadata
            images.removeAll { it.id == id }
            saveAllImageMetadata(images)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Restore an image from the vault back to the device gallery.
     * @param id The vault image ID to restore
     * @return URI of restored image if successful, null otherwise
     */
    suspend fun restoreFromVault(id: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val image = loadImageMetadata().find { it.id == id } ?: return@withContext null
            val encryptedFile = File(image.encryptedPath)
            if (!encryptedFile.exists()) return@withContext null

            // Get decrypted input stream
            val inputStream = getDecryptedStream(id) ?: return@withContext null

            // Save to MediaStore
            val uri = saveToMediaStore(inputStream, image.originalName, image.mimeType)

            inputStream.close()

            // If save was successful, remove from vault
            if (uri != null) {
                removeFromVault(id)
            }

            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get a decrypted input stream for displaying a vault image.
     * @param id The vault image ID
     * @return InputStream of decrypted image data, or null if not found
     */
    fun getDecryptedStream(id: String): InputStream? {
        val image = loadImageMetadata().find { it.id == id } ?: return null
        val encryptedFile = File(image.encryptedPath)
        if (!encryptedFile.exists()) return null

        return try {
            val masterKey = SecurityManager.getMasterKey(context)
            val encrypted = EncryptedFile.Builder(
                context,
                encryptedFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encrypted.openFileInput()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get all vault images.
     * @return List of VaultImage objects
     */
    suspend fun listVaultImages(): List<VaultImage> = withContext(Dispatchers.IO) {
        loadImageMetadata().sortedByDescending { it.dateAdded }
    }

    /**
     * Get a specific vault image by ID.
     */
    fun getVaultImage(id: String): VaultImage? {
        return loadImageMetadata().find { it.id == id }
    }

    /**
     * Get the count of images in the vault.
     */
    fun getVaultImageCount(): Int {
        return loadImageMetadata().size
    }

    private fun getExtensionFromUri(uri: Uri): String? {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        }
    }

    private fun saveImageMetadata(image: VaultImage) {
        val images = loadImageMetadata().toMutableList()
        images.add(image)
        saveAllImageMetadata(images)
    }

    private fun saveAllImageMetadata(images: List<VaultImage>) {
        val jsonArray = JSONArray()
        images.forEach { img ->
            val obj = JSONObject().apply {
                put("id", img.id)
                put("originalName", img.originalName)
                put("encryptedPath", img.encryptedPath)
                put("dateAdded", img.dateAdded)
                put("size", img.size)
                put("mimeType", img.mimeType)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_VAULT_IMAGES, jsonArray.toString()).apply()
    }

    private fun loadImageMetadata(): List<VaultImage> {
        val json = prefs.getString(KEY_VAULT_IMAGES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                VaultImage(
                    id = obj.getString("id"),
                    originalName = obj.getString("originalName"),
                    encryptedPath = obj.getString("encryptedPath"),
                    dateAdded = obj.getLong("dateAdded"),
                    size = obj.getLong("size"),
                    mimeType = obj.optString("mimeType", "image/*")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveToMediaStore(inputStream: InputStream, displayName: String, mimeType: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return uri?.let {
            resolver.openOutputStream(it)?.use { output ->
                inputStream.copyTo(output)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }

            it
        }
    }
}

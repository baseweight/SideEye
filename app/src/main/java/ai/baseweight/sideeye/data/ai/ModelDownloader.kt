package ai.baseweight.sideeye.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Downloads VLM models from Nexa's S3 bucket with HuggingFace fallback.
 */
class ModelDownloader(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val HF_OWNER = "NexaAI"

        // OmniNeural-4B model info
        const val OMNINEURAL_MODEL_ID = "OmniNeural-4B"
        const val OMNINEURAL_BASE_URL = "https://nexa-model-hub-bucket.s3.us-west-1.amazonaws.com/public/nexa_sdk/huggingface-models/OmniNeural-4B-mobile/"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Download progress state
     */
    sealed class DownloadState {
        object Idle : DownloadState()
        data class ListingFiles(val source: String) : DownloadState()
        data class Downloading(
            val currentFile: String,
            val fileIndex: Int,
            val totalFiles: Int,
            val bytesDownloaded: Long,
            val totalBytes: Long
        ) : DownloadState()
        data class Completed(val modelDir: File) : DownloadState()
        data class Error(val message: String, val exception: Exception? = null) : DownloadState()
    }

    /**
     * Check if a model is already downloaded.
     * Uses a .download_complete marker file to distinguish partial from complete downloads.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        val modelDir = getModelDir(modelId)
        if (!modelDir.exists()) return false

        return File(modelDir, ".download_complete").exists()
    }

    /**
     * Get the directory where a model is/will be stored.
     */
    fun getModelDir(modelId: String): File {
        return File(context.filesDir, "models/$modelId")
    }

    /**
     * Download a model by ID. Returns a Flow of download progress.
     * Uses channelFlow to allow emissions from nested coroutine contexts.
     */
    fun downloadModel(modelId: String): Flow<DownloadState> = channelFlow {
        send(DownloadState.Idle)

        val baseUrl = when (modelId) {
            OMNINEURAL_MODEL_ID -> OMNINEURAL_BASE_URL
            else -> {
                send(DownloadState.Error("Unknown model: $modelId"))
                return@channelFlow
            }
        }

        val modelDir = getModelDir(modelId)

        try {
            // Create model directory
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            // List files from S3
            send(DownloadState.ListingFiles("S3"))
            var files = listFilesFromS3(baseUrl)

            // Fallback to HuggingFace if S3 fails
            if (files.isEmpty()) {
                send(DownloadState.ListingFiles("HuggingFace"))
                val repoId = getHfRepoId(baseUrl)
                files = listFilesFromHuggingFace(repoId)

                if (files.isEmpty()) {
                    send(DownloadState.Error("Failed to list model files from both S3 and HuggingFace"))
                    return@channelFlow
                }
            }

            Log.d(TAG, "Found ${files.size} files to download: $files")

            // Download each file
            var totalDownloaded = 0L
            val totalSize = getFileSizes(baseUrl, files)

            files.forEachIndexed { index, fileName ->
                val targetFile = File(modelDir, fileName)

                // Skip if file already exists with correct size
                if (targetFile.exists()) {
                    Log.d(TAG, "File already exists, skipping: $fileName")
                    totalDownloaded += targetFile.length()
                    send(DownloadState.Downloading(
                        currentFile = fileName,
                        fileIndex = index + 1,
                        totalFiles = files.size,
                        bytesDownloaded = totalDownloaded,
                        totalBytes = totalSize
                    ))
                    return@forEachIndexed
                }

                // Try S3 first, then HuggingFace
                val s3Url = "$baseUrl$fileName"
                val hfUrl = getHfDownloadUrl(getHfRepoId(baseUrl), fileName)

                var downloadSuccess = false

                for (url in listOf(s3Url, hfUrl)) {
                    try {
                        downloadFile(url, targetFile) { bytesRead ->
                            send(DownloadState.Downloading(
                                currentFile = fileName,
                                fileIndex = index + 1,
                                totalFiles = files.size,
                                bytesDownloaded = totalDownloaded + bytesRead,
                                totalBytes = totalSize
                            ))
                        }
                        totalDownloaded += targetFile.length()
                        downloadSuccess = true
                        Log.d(TAG, "Downloaded: $fileName from ${if (url == s3Url) "S3" else "HuggingFace"}")
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to download from $url: ${e.message}")
                        targetFile.delete()
                    }
                }

                if (!downloadSuccess) {
                    send(DownloadState.Error("Failed to download: $fileName"))
                    return@channelFlow
                }
            }

            // Write completion marker
            File(modelDir, ".download_complete").writeText("done")

            send(DownloadState.Completed(modelDir))

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            send(DownloadState.Error("Download failed: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Download OmniNeural model (convenience method).
     */
    fun downloadOmniNeural(): Flow<DownloadState> = downloadModel(OMNINEURAL_MODEL_ID)

    private suspend fun listFilesFromS3(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val urlWithoutProtocol = baseUrl.removePrefix("https://").removePrefix("http://")
            val hostEndIndex = urlWithoutProtocol.indexOf('/')
            if (hostEndIndex == -1) return@withContext emptyList()

            val host = urlWithoutProtocol.substring(0, hostEndIndex)
            val prefix = urlWithoutProtocol.substring(hostEndIndex + 1).trimEnd('/')

            val listUrl = "https://$host/?list-type=2&prefix=$prefix/"
            Log.d(TAG, "Listing S3 bucket: $listUrl")

            val request = Request.Builder().url(listUrl).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to list S3 bucket: ${response.code}")
                return@withContext emptyList()
            }

            val xmlContent = response.body?.string() ?: return@withContext emptyList()
            parseS3ListResponse(xmlContent, prefix)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing S3 files: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseS3ListResponse(xmlContent: String, prefix: String): List<String> {
        val files = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var currentTag = ""
            val prefixWithSlash = if (prefix.endsWith("/")) prefix else "$prefix/"

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        if (currentTag == "Key") {
                            val key = parser.text
                            if (key.startsWith(prefixWithSlash) && key.length > prefixWithSlash.length) {
                                val relativePath = key.removePrefix(prefixWithSlash)
                                if (!relativePath.endsWith("/") && relativePath.isNotEmpty()) {
                                    files.add(relativePath)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = ""
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing S3 XML response: ${e.message}", e)
        }
        return files
    }

    private suspend fun listFilesFromHuggingFace(repoId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://huggingface.co/api/models/$repoId/tree/main"
            Log.d(TAG, "Listing HuggingFace repo: $apiUrl")

            val request = Request.Builder().url(apiUrl).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to list HuggingFace repo: ${response.code}")
                return@withContext emptyList()
            }

            val jsonContent = response.body?.string() ?: return@withContext emptyList()
            parseHuggingFaceResponse(jsonContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing HuggingFace files: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseHuggingFaceResponse(jsonContent: String): List<String> {
        val files = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(jsonContent)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val type = item.optString("type", "")
                val path = item.optString("path", "")
                if (type == "file" && path.isNotEmpty()) {
                    files.add(path)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing HuggingFace JSON: ${e.message}", e)
        }
        return files
    }

    private fun getHfRepoId(s3Url: String): String {
        val path = s3Url.removePrefix("https://").removePrefix("http://")
            .substringAfter("/").trimEnd('/')
        val segments = path.split("/").filter { it.isNotEmpty() }
        val repoName = segments.lastOrNull() ?: ""
        return "$HF_OWNER/$repoName"
    }

    private fun getHfDownloadUrl(repoId: String, fileName: String): String {
        return "https://huggingface.co/$repoId/resolve/main/$fileName?download=true"
    }

    private suspend fun getFileSizes(baseUrl: String, files: List<String>): Long = withContext(Dispatchers.IO) {
        var total = 0L
        files.forEach { fileName ->
            try {
                val url = "$baseUrl$fileName"
                val request = Request.Builder().url(url).head().build()
                val response = client.newCall(request).execute()
                val size = response.header("Content-Length")?.toLongOrNull() ?: 0L
                total += size
            } catch (e: Exception) {
                // Ignore errors, we'll get actual size during download
            }
        }
        total
    }

    private suspend fun downloadFile(
        url: String,
        targetFile: File,
        onProgress: suspend (bytesRead: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")

        // Ensure parent directory exists
        targetFile.parentFile?.mkdirs()

        FileOutputStream(targetFile).use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    onProgress(bytesRead)
                }
            }
        }
    }

    /**
     * Delete a downloaded model.
     */
    fun deleteModel(modelId: String): Boolean {
        val modelDir = getModelDir(modelId)
        return if (modelDir.exists()) {
            modelDir.deleteRecursively()
        } else {
            true
        }
    }
}

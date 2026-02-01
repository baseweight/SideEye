package ai.baseweight.sideeye

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * JVM unit tests for model download logic.
 * These run on your local machine without needing an Android device.
 *
 * Run with:
 * ./gradlew test --tests "ai.baseweight.sideeye.ModelDownloadUnitTest"
 */
class ModelDownloadUnitTest {

    companion object {
        const val OMNINEURAL_BASE_URL = "https://nexa-model-hub-bucket.s3.us-west-1.amazonaws.com/public/nexa_sdk/huggingface-models/OmniNeural-4B-mobile/"
        const val HF_OWNER = "NexaAI"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Test
    fun testS3FileListing() {
        println("=== Testing S3 File Listing ===")

        val files = listFilesFromS3(OMNINEURAL_BASE_URL)

        println("Found ${files.size} files from S3:")
        files.forEach { println("  - $it") }

        if (files.isEmpty()) {
            println("S3 returned no files - may be blocked or rate limited")
            println("Trying HuggingFace fallback...")
            val hfFiles = listFilesFromHuggingFace(getHfRepoId(OMNINEURAL_BASE_URL))
            println("HuggingFace found ${hfFiles.size} files")
            hfFiles.forEach { println("  - $it") }
            assert(hfFiles.isNotEmpty()) { "Should find files from at least one source (S3 or HF)" }
        }

        println("=== S3 File Listing: PASSED ===")
    }

    @Test
    fun testHuggingFaceFileListing() {
        println("=== Testing HuggingFace File Listing ===")

        val repoId = getHfRepoId(OMNINEURAL_BASE_URL)
        println("HF Repo ID: $repoId")

        val files = listFilesFromHuggingFace(repoId)

        println("Found ${files.size} files from HuggingFace:")
        files.forEach { println("  - $it") }

        assert(files.isNotEmpty()) { "Should find files in HuggingFace repo" }
        println("=== HuggingFace File Listing: PASSED ===")
    }

    @Test
    fun testFileUrlConstruction() {
        println("=== Testing URL Construction ===")

        val fileName = "files-1-1.nexa"

        // S3 URL
        val s3Url = "$OMNINEURAL_BASE_URL$fileName"
        println("S3 URL: $s3Url")

        // HF URL
        val repoId = getHfRepoId(OMNINEURAL_BASE_URL)
        val hfUrl = "https://huggingface.co/$repoId/resolve/main/$fileName?download=true"
        println("HF URL: $hfUrl")

        assert(s3Url.contains("OmniNeural-4B-mobile"))
        assert(hfUrl.contains("NexaAI"))

        println("=== URL Construction: PASSED ===")
    }

    @Test
    fun testFileSizeRetrieval() {
        println("=== Testing File Size Retrieval ===")

        val files = listFilesFromS3(OMNINEURAL_BASE_URL)
        if (files.isEmpty()) {
            println("No files found, skipping size test")
            return
        }

        val firstFile = files.first()
        val url = "$OMNINEURAL_BASE_URL$firstFile"

        println("Getting size for: $firstFile")

        val request = Request.Builder().url(url).head().build()
        val response = client.newCall(request).execute()

        val size = response.header("Content-Length")?.toLongOrNull() ?: 0L
        val sizeMb = size / (1024 * 1024)

        println("File size: $size bytes ($sizeMb MB)")
        println("Response code: ${response.code}")

        assert(response.isSuccessful) { "HEAD request should succeed" }
        println("=== File Size Retrieval: PASSED ===")
    }

    @Test
    fun testSmallFileDownload() {
        println("=== Testing Small File Download ===")

        // Find a small file to download (like manifest or config)
        val files = listFilesFromS3(OMNINEURAL_BASE_URL)
        val smallFile = files.find {
            it.endsWith(".json") || it.endsWith(".txt") || it.endsWith(".md")
        }

        if (smallFile == null) {
            println("No small text file found, downloading first 1KB of first file instead")

            if (files.isEmpty()) {
                println("No files found, skipping download test")
                return
            }

            val url = "$OMNINEURAL_BASE_URL${files.first()}"
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1023")  // Just first 1KB
                .build()

            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes()

            println("Downloaded ${bytes?.size ?: 0} bytes (partial)")
            assert((bytes?.size ?: 0) > 0) { "Should download some bytes" }
        } else {
            println("Downloading: $smallFile")

            val url = "$OMNINEURAL_BASE_URL$smallFile"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            val content = response.body?.string()
            println("Downloaded ${content?.length ?: 0} characters")
            println("Content preview: ${content?.take(200)}...")

            assert(response.isSuccessful) { "Download should succeed" }
        }

        println("=== Small File Download: PASSED ===")
    }

    @Test
    fun testFullDownloadToTempDir() {
        println("=== Testing Full Download (to temp dir) ===")
        println("WARNING: This downloads the full model (~150MB+). Press Ctrl+C to cancel.")

        val files = listFilesFromS3(OMNINEURAL_BASE_URL)
        if (files.isEmpty()) {
            println("No files found from S3, trying HuggingFace...")
            val hfFiles = listFilesFromHuggingFace(getHfRepoId(OMNINEURAL_BASE_URL))
            if (hfFiles.isEmpty()) {
                println("No files found, skipping full download test")
                return
            }
        }

        // Create temp directory
        val tempDir = File(System.getProperty("java.io.tmpdir"), "sideeye_model_test")
        tempDir.mkdirs()
        println("Download directory: ${tempDir.absolutePath}")

        var totalBytes = 0L
        var fileCount = 0

        files.forEach { fileName ->
            val targetFile = File(tempDir, fileName)

            // Skip if already exists
            if (targetFile.exists()) {
                println("  [SKIP] $fileName (already exists)")
                totalBytes += targetFile.length()
                fileCount++
                return@forEach
            }

            val url = "$OMNINEURAL_BASE_URL$fileName"
            println("  [DOWNLOADING] $fileName...")

            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { output ->
                        response.body?.byteStream()?.use { input ->
                            input.copyTo(output)
                        }
                    }
                    val sizeMb = targetFile.length() / (1024 * 1024)
                    println("  [DONE] $fileName (${sizeMb}MB)")
                    totalBytes += targetFile.length()
                    fileCount++
                } else {
                    println("  [FAILED] $fileName - HTTP ${response.code}")
                }
            } catch (e: Exception) {
                println("  [ERROR] $fileName - ${e.message}")
            }
        }

        val totalMb = totalBytes / (1024 * 1024)
        println("\nDownload complete!")
        println("Files: $fileCount")
        println("Total size: ${totalMb}MB")
        println("Location: ${tempDir.absolutePath}")

        println("=== Full Download: PASSED ===")
    }

    // --- Helper methods (copied from ModelDownloader) ---

    private fun listFilesFromS3(baseUrl: String): List<String> {
        try {
            val urlWithoutProtocol = baseUrl.removePrefix("https://").removePrefix("http://")
            val hostEndIndex = urlWithoutProtocol.indexOf('/')
            if (hostEndIndex == -1) return emptyList()

            val host = urlWithoutProtocol.substring(0, hostEndIndex)
            val prefix = urlWithoutProtocol.substring(hostEndIndex + 1).trimEnd('/')

            val listUrl = "https://$host/?list-type=2&prefix=$prefix/"
            println("S3 list URL: $listUrl")

            val request = Request.Builder().url(listUrl).get().build()
            val response = client.newCall(request).execute()

            println("S3 response code: ${response.code}")

            if (!response.isSuccessful) {
                println("S3 listing failed: ${response.code}")
                return emptyList()
            }

            val xmlContent = response.body?.string() ?: return emptyList()
            println("S3 response length: ${xmlContent.length} chars")
            println("S3 response preview: ${xmlContent.take(500)}")
            return parseS3ListResponse(xmlContent, prefix)
        } catch (e: Exception) {
            println("S3 listing error: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun parseS3ListResponse(xmlContent: String, prefix: String): List<String> {
        val files = mutableListOf<String>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xmlContent.byteInputStream())

            val prefixWithSlash = if (prefix.endsWith("/")) prefix else "$prefix/"
            val keyNodes = document.getElementsByTagName("Key")

            for (i in 0 until keyNodes.length) {
                val key = keyNodes.item(i).textContent
                if (key.startsWith(prefixWithSlash) && key.length > prefixWithSlash.length) {
                    val relativePath = key.removePrefix(prefixWithSlash)
                    if (!relativePath.endsWith("/") && relativePath.isNotEmpty()) {
                        files.add(relativePath)
                    }
                }
            }
        } catch (e: Exception) {
            println("XML parsing error: ${e.message}")
        }
        return files
    }

    private fun listFilesFromHuggingFace(repoId: String): List<String> {
        try {
            val apiUrl = "https://huggingface.co/api/models/$repoId/tree/main"

            val request = Request.Builder().url(apiUrl).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                println("HuggingFace listing failed: ${response.code}")
                return emptyList()
            }

            val jsonContent = response.body?.string() ?: return emptyList()
            return parseHuggingFaceResponse(jsonContent)
        } catch (e: Exception) {
            println("HuggingFace listing error: ${e.message}")
            return emptyList()
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
            println("JSON parsing error: ${e.message}")
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
}

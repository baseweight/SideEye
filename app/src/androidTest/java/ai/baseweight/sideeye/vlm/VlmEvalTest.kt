package ai.baseweight.sideeye.vlm

import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.baseweight.sideeye.data.ai.ModelDownloader
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumentation test for evaluating OmniNeural VLM on Qualcomm NPU.
 *
 * Run with:
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ai.baseweight.sideeye.vlm.VlmEvalTest
 *
 * Results are written to: /sdcard/Android/data/ai.baseweight.sideeye/files/vlm_eval_results/
 */
@RunWith(AndroidJUnit4::class)
class VlmEvalTest {

    companion object {
        private const val TAG = "VlmEvalTest"
        private const val RESULTS_DIR = "vlm_eval_results"

        // Prompt for content moderation classification
        private const val MODERATION_PROMPT = """Analyze this image and classify it. Reply with ONLY the applicable categories from this list, separated by commas:
- NUDITY: Contains nudity or revealing content
- DRINKING: Shows alcohol consumption
- DRUGS: Shows drug use or paraphernalia
- EMBARRASSING: Potentially embarrassing or unflattering
- SAFE: None of the above apply

Categories:"""

        // Maximum images to use from gallery for testing
        private const val MAX_GALLERY_IMAGES = 10
    }

    private lateinit var context: android.content.Context
    private lateinit var benchmarkRunner: VlmBenchmarkRunner
    private lateinit var downloader: ModelDownloader
    private lateinit var resultsDir: File

    private var benchmarkResult: BenchmarkResult? = null

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        benchmarkRunner = VlmBenchmarkRunner(context)
        downloader = ModelDownloader(context)

        // Create results directory
        resultsDir = File(context.getExternalFilesDir(null), RESULTS_DIR)
        if (!resultsDir.exists()) {
            resultsDir.mkdirs()
        }

        Log.i(TAG, "Results will be written to: ${resultsDir.absolutePath}")
    }

    /**
     * Ensures the OmniNeural model is downloaded before running tests.
     * Call this at the start of any test that needs the model.
     */
    private suspend fun ensureModelDownloaded(): Boolean {
        if (downloader.isModelDownloaded(ModelDownloader.OMNINEURAL_MODEL_ID)) {
            Log.i(TAG, "Model already downloaded")
            return true
        }

        Log.i(TAG, "Model not found, starting download...")

        var success = false
        downloader.downloadOmniNeural().collect { state ->
            when (state) {
                is ModelDownloader.DownloadState.ListingFiles -> {
                    Log.i(TAG, "Listing files from ${state.source}...")
                }
                is ModelDownloader.DownloadState.Downloading -> {
                    val percent = if (state.totalBytes > 0) {
                        (state.bytesDownloaded * 100 / state.totalBytes).toInt()
                    } else 0
                    if (percent % 10 == 0) { // Log every 10%
                        Log.i(TAG, "Download: ${state.currentFile} [${state.fileIndex}/${state.totalFiles}] $percent%")
                    }
                }
                is ModelDownloader.DownloadState.Completed -> {
                    Log.i(TAG, "Model download completed: ${state.modelDir}")
                    success = true
                }
                is ModelDownloader.DownloadState.Error -> {
                    Log.e(TAG, "Model download failed: ${state.message}")
                    success = false
                }
                else -> {}
            }
        }

        return success
    }

    @After
    fun teardown() {
        benchmarkResult?.let { result ->
            writeResultToFile(result, "omnineural_results.json")
        }
    }

    /**
     * Full benchmark of OmniNeural on NPU.
     */
    @Test
    fun benchmarkOmniNeural() = runBlocking {
        Log.i(TAG, "=== Starting OmniNeural NPU Benchmark ===")

        // Ensure model is downloaded
        if (!ensureModelDownloaded()) {
            Log.e(TAG, "Failed to download model. Aborting benchmark.")
            return@runBlocking
        }

        val adapter = OmniNeuralAdapter()
        val testImages = loadTestImages()

        if (testImages.isEmpty()) {
            Log.w(TAG, "No test images found. Skipping benchmark.")
            return@runBlocking
        }

        benchmarkResult = benchmarkRunner.runBenchmark(
            adapter = adapter,
            testImages = testImages,
            prompt = MODERATION_PROMPT,
            warmupRuns = 2,
            labelMatcher = ::matchModerationLabel
        )

        Log.i(TAG, "=== OmniNeural Benchmark Complete ===")
        println(benchmarkResult?.toSummaryString())
    }

    /**
     * Quick sanity test with 2 images.
     */
    @Test
    fun quickSanityTest() = runBlocking {
        Log.i(TAG, "=== Starting Quick Sanity Test ===")

        // Ensure model is downloaded
        if (!ensureModelDownloaded()) {
            Log.e(TAG, "Failed to download model. Aborting test.")
            return@runBlocking
        }

        val adapter = OmniNeuralAdapter()
        val testImages = loadTestImages().take(2)

        if (testImages.isEmpty()) {
            Log.w(TAG, "No test images found.")

            // Still test initialization
            val initStart = System.currentTimeMillis()
            val success = adapter.initialize(context)
            val initTime = System.currentTimeMillis() - initStart
            Log.i(TAG, "OmniNeural init: success=$success, time=${initTime}ms")
            adapter.release()
            return@runBlocking
        }

        Log.i(TAG, "Testing with ${testImages.size} images...")

        val success = adapter.initialize(context)
        if (success) {
            testImages.forEach { image ->
                try {
                    val start = System.currentTimeMillis()
                    val response = adapter.analyze(context, image.uri, MODERATION_PROMPT)
                    val elapsed = System.currentTimeMillis() - start
                    Log.i(TAG, "Image ${image.id}: ${elapsed}ms")
                    Log.i(TAG, "Response: $response")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed on ${image.id}", e)
                }
            }
            adapter.release()
        } else {
            Log.e(TAG, "OmniNeural initialization failed")
        }

        Log.i(TAG, "=== Quick Sanity Test Complete ===")
    }

    /**
     * Test just model initialization (no inference).
     */
    @Test
    fun testModelInitialization() = runBlocking {
        Log.i(TAG, "=== Testing Model Initialization ===")

        // Ensure model is downloaded
        if (!ensureModelDownloaded()) {
            Log.e(TAG, "Failed to download model. Aborting test.")
            return@runBlocking
        }

        val adapter = OmniNeuralAdapter()

        val initStart = System.currentTimeMillis()
        val success = adapter.initialize(context)
        val initTime = System.currentTimeMillis() - initStart

        Log.i(TAG, "Initialization: success=$success, time=${initTime}ms")
        println("OmniNeural initialization: success=$success, time=${initTime}ms")

        if (success) {
            assert(adapter.isReady()) { "Adapter should be ready after successful init" }
            adapter.release()
            assert(!adapter.isReady()) { "Adapter should not be ready after release" }
        }

        Log.i(TAG, "=== Initialization Test Complete ===")
    }

    /**
     * Load test images from device gallery or test assets.
     */
    private fun loadTestImages(): List<TestImage> {
        val images = mutableListOf<TestImage>()

        // First, try to load from test assets directory
        val assetsDir = File(context.getExternalFilesDir(null), "test_images")
        if (assetsDir.exists() && assetsDir.isDirectory) {
            Log.i(TAG, "Loading test images from: ${assetsDir.absolutePath}")

            assetsDir.listFiles()?.forEach { file ->
                if (file.isFile && isImageFile(file.name)) {
                    val uri = android.net.Uri.fromFile(file)
                    val label = extractLabelFromFilename(file.name)
                    images.add(TestImage(
                        id = file.name,
                        uri = uri,
                        expectedLabel = label
                    ))
                }
            }
        }

        // If no test images found, fall back to device gallery
        if (images.isEmpty()) {
            Log.i(TAG, "No test images in assets. Loading from device gallery...")
            images.addAll(loadFromGallery(MAX_GALLERY_IMAGES))
        }

        Log.i(TAG, "Loaded ${images.size} test images")
        return images
    }

    private fun loadFromGallery(maxImages: Int): List<TestImage> {
        val images = mutableListOf<TestImage>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
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

            var count = 0
            while (cursor.moveToNext() && count < maxImages) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                images.add(TestImage(
                    id = name,
                    uri = uri,
                    expectedLabel = null
                ))
                count++
            }
        }

        return images
    }

    private fun extractLabelFromFilename(filename: String): String? {
        val nameWithoutExt = filename.substringBeforeLast(".")
        val parts = nameWithoutExt.split("_", "-", limit = 2)

        if (parts.size >= 2) {
            val potentialLabel = parts[0].uppercase()
            return when (potentialLabel) {
                "NUDITY", "DRINKING", "DRUGS", "EMBARRASSING", "SAFE" -> potentialLabel
                else -> null
            }
        }
        return null
    }

    private fun matchModerationLabel(response: String, expectedLabel: String): Boolean {
        return response.uppercase().contains(expectedLabel.uppercase())
    }

    private fun isImageFile(filename: String): Boolean {
        val ext = filename.substringAfterLast(".").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "webp", "bmp")
    }

    private fun writeResultToFile(result: BenchmarkResult, filename: String) {
        try {
            val file = File(resultsDir, filename)
            file.writeText(result.toJson().toString(2))
            Log.i(TAG, "Results written to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write results to file", e)
        }
    }
}

package ai.baseweight.sideeye.vlm

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.baseweight.sideeye.data.ai.ModelDownloader
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tests for model download functionality.
 * Can be run locally or on Qualcomm Device Cloud.
 *
 * Run with:
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ai.baseweight.sideeye.vlm.ModelDownloadTest
 */
@RunWith(AndroidJUnit4::class)
class ModelDownloadTest {

    companion object {
        private const val TAG = "ModelDownloadTest"
    }

    private lateinit var context: android.content.Context
    private lateinit var downloader: ModelDownloader

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        downloader = ModelDownloader(context)
    }

    /**
     * Test downloading OmniNeural model.
     * This is a long-running test (model is ~150MB+).
     */
    @Test
    fun downloadOmniNeural(): Unit = runBlocking {
        Log.i(TAG, "=== Starting OmniNeural Download Test ===")

        // Check if already downloaded
        if (downloader.isModelDownloaded(ModelDownloader.OMNINEURAL_MODEL_ID)) {
            Log.i(TAG, "Model already downloaded, skipping download")
            val modelDir = downloader.getModelDir(ModelDownloader.OMNINEURAL_MODEL_ID)
            printModelFiles(modelDir)
            return@runBlocking
        }

        var lastState: ModelDownloader.DownloadState? = null

        downloader.downloadOmniNeural().collect { state ->
            lastState = state
            when (state) {
                is ModelDownloader.DownloadState.Idle -> {
                    Log.i(TAG, "Download idle")
                }
                is ModelDownloader.DownloadState.ListingFiles -> {
                    Log.i(TAG, "Listing files from ${state.source}...")
                }
                is ModelDownloader.DownloadState.Downloading -> {
                    val percent = if (state.totalBytes > 0) {
                        (state.bytesDownloaded * 100 / state.totalBytes).toInt()
                    } else {
                        0
                    }
                    val mbDownloaded = state.bytesDownloaded / (1024 * 1024)
                    val mbTotal = state.totalBytes / (1024 * 1024)
                    Log.i(TAG, "Downloading [${state.fileIndex}/${state.totalFiles}] ${state.currentFile}: ${mbDownloaded}MB / ${mbTotal}MB ($percent%)")
                }
                is ModelDownloader.DownloadState.Completed -> {
                    Log.i(TAG, "Download completed: ${state.modelDir.absolutePath}")
                    printModelFiles(state.modelDir)
                }
                is ModelDownloader.DownloadState.Error -> {
                    Log.e(TAG, "Download error: ${state.message}", state.exception)
                }
            }
        }

        // Verify download succeeded
        assert(lastState is ModelDownloader.DownloadState.Completed) {
            "Download did not complete successfully: $lastState"
        }

        assert(downloader.isModelDownloaded(ModelDownloader.OMNINEURAL_MODEL_ID)) {
            "Model not marked as downloaded after completion"
        }

        Log.i(TAG, "=== OmniNeural Download Test Complete ===")
    }

    /**
     * Quick test to verify S3 file listing works.
     */
    @Test
    fun testFileListing(): Unit = runBlocking {
        Log.i(TAG, "=== Testing File Listing ===")

        var listedFiles = false

        downloader.downloadOmniNeural().collect { state ->
            when (state) {
                is ModelDownloader.DownloadState.ListingFiles -> {
                    Log.i(TAG, "Listing from: ${state.source}")
                }
                is ModelDownloader.DownloadState.Downloading -> {
                    // We got files, listing worked
                    listedFiles = true
                    Log.i(TAG, "Found files, first: ${state.currentFile}, total: ${state.totalFiles}")
                }
                is ModelDownloader.DownloadState.Error -> {
                    Log.e(TAG, "Error: ${state.message}")
                }
                else -> {}
            }
        }

        Log.i(TAG, "File listing test: ${if (listedFiles) "PASSED" else "FAILED"}")
        Log.i(TAG, "=== File Listing Test Complete ===")
    }

    /**
     * Test that checks if model is already present (no network).
     */
    @Test
    fun testModelPresenceCheck() {
        Log.i(TAG, "=== Testing Model Presence Check ===")

        val isDownloaded = downloader.isModelDownloaded(ModelDownloader.OMNINEURAL_MODEL_ID)
        val modelDir = downloader.getModelDir(ModelDownloader.OMNINEURAL_MODEL_ID)

        Log.i(TAG, "Model ID: ${ModelDownloader.OMNINEURAL_MODEL_ID}")
        Log.i(TAG, "Model dir: ${modelDir.absolutePath}")
        Log.i(TAG, "Dir exists: ${modelDir.exists()}")
        Log.i(TAG, "Is downloaded: $isDownloaded")

        if (modelDir.exists()) {
            printModelFiles(modelDir)
        }

        Log.i(TAG, "=== Model Presence Check Complete ===")
    }

    private fun printModelFiles(dir: File) {
        Log.i(TAG, "Model files in ${dir.absolutePath}:")
        dir.listFiles()?.forEach { file ->
            val sizeMb = file.length() / (1024 * 1024)
            Log.i(TAG, "  - ${file.name} (${sizeMb}MB)")
        }
    }
}

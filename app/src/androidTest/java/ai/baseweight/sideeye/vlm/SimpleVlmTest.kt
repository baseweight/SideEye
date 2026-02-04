package ai.baseweight.sideeye.vlm

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.baseweight.sideeye.data.ai.ModelDownloader
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.GenerationConfig
import com.nexa.sdk.bean.LlmStreamResult
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.VlmChatMessage
import com.nexa.sdk.bean.VlmContent
import com.nexa.sdk.bean.VlmCreateInput
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Simple VLM test to verify OmniNeural image understanding.
 *
 * Setup:
 * 1. Push test image: adb push ~/packraft.jpg /sdcard/Android/data/ai.baseweight.sideeye/files/
 * 2. Run: adb shell am instrument -w -e class ai.baseweight.sideeye.vlm.SimpleVlmTest#describeImage ai.baseweight.sideeye.test/androidx.test.runner.AndroidJUnitRunner
 * 3. Check logcat: adb logcat -s SimpleVlmTest
 */
@RunWith(AndroidJUnit4::class)
class SimpleVlmTest {

    companion object {
        private const val TAG = "SimpleVlmTest"
    }

    private lateinit var context: android.content.Context
    private lateinit var downloader: ModelDownloader
    private var vlmWrapper: VlmWrapper? = null

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        downloader = ModelDownloader(context)
    }

    @After
    fun teardown() {
        runBlocking {
            vlmWrapper?.let {
                it.stopStream()
                it.destroy()
            }
        }
        vlmWrapper = null
    }

    @Test
    fun describeImage(): Unit = runBlocking {
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Simple OmniNeural VLM Image Description Test")
        Log.i(TAG, "===========================================")

        // 1. Check for test image
        val testImage = File(context.getExternalFilesDir(null), "packraft.jpg")
        if (!testImage.exists()) {
            Log.e(TAG, "ERROR: Test image not found at ${testImage.absolutePath}")
            Log.e(TAG, "Please run: adb push ~/packraft.jpg /sdcard/Android/data/ai.baseweight.sideeye/files/")
            return@runBlocking
        }
        Log.i(TAG, "Found test image: ${testImage.absolutePath}")

        // 3. Check model is downloaded
        if (!downloader.isModelDownloaded(ModelDownloader.OMNINEURAL_MODEL_ID)) {
            Log.e(TAG, "ERROR: OmniNeural model not downloaded")
            Log.e(TAG, "Run ModelDownloadTest#downloadOmniNeural first")
            return@runBlocking
        }
        Log.i(TAG, "Model is downloaded")

        // 4. Initialize model
        Log.i(TAG, "Initializing VLM...")
        val initStart = System.currentTimeMillis()

        NexaSdk.getInstance().init(context)
        val modelDir = downloader.getModelDir(ModelDownloader.OMNINEURAL_MODEL_ID)

        val config = ModelConfig(
            nCtx = 2048,
            nThreads = 8,
            enable_thinking = false,
            npu_lib_folder_path = context.applicationInfo.nativeLibraryDir,
            npu_model_folder_path = modelDir.absolutePath
        )

        var initSuccess = false
        VlmWrapper.builder()
            .vlmCreateInput(
                VlmCreateInput(
                    model_name = "omni-neural",
                    model_path = File(modelDir, "files-1-1.nexa").absolutePath,
                    mmproj_path = null,
                    config = config,
                    plugin_id = "npu"
                )
            )
            .build()
            .onSuccess { wrapper ->
                vlmWrapper = wrapper
                initSuccess = true
            }
            .onFailure { error ->
                Log.e(TAG, "Model init failed", error)
            }

        if (!initSuccess) {
            Log.e(TAG, "ERROR: Failed to initialize model")
            return@runBlocking
        }

        val initTime = System.currentTimeMillis() - initStart
        Log.i(TAG, "Model initialized in ${initTime}ms")

        // 5. Build prompt with image (matching Python SDK pattern)
        val prompt = "Describe this image in detail."
        val imagePath = testImage.absolutePath

        Log.i(TAG, "Prompt: $prompt")
        Log.i(TAG, "Image path: $imagePath")

        // Build contents like Python: VlmContent(type="image", text=image_path)
        val contents = listOf(
            VlmContent("image", imagePath),
            VlmContent("text", prompt)
        )
        val chatList = arrayListOf(VlmChatMessage("user", contents))
        val chatArray = chatList.toTypedArray()

        // 6. Apply chat template
        val templateResult = vlmWrapper!!.applyChatTemplate(chatArray, null, false)
        val formattedText = templateResult.getOrElse {
            Log.e(TAG, "ERROR: applyChatTemplate failed", it)
            return@runBlocking
        }.formattedText

        Log.i(TAG, "Formatted text length: ${formattedText.length}")
        Log.i(TAG, "Formatted text: $formattedText")

        // 7. Create config with image_paths directly (like Python SDK)
        // Python: GenerationConfig(max_tokens=2048, image_paths=image_paths, image_max_length=512)
        val genConfig = GenerationConfig(
            maxTokens = 512,
            imagePaths = arrayOf(imagePath),
            imageCount = 1
        )

        Log.i(TAG, "Config imagePaths: ${genConfig.imagePaths?.joinToString()}")
        Log.i(TAG, "Config imageCount: ${genConfig.imageCount}")

        // 8. Generate response
        Log.i(TAG, "")
        Log.i(TAG, "=== Generating response ===")

        val responseBuilder = StringBuilder()
        val genStart = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var tokenCount = 0

        try {
            vlmWrapper!!.generateStreamFlow(formattedText, genConfig).collect { result ->
                when (result) {
                    is LlmStreamResult.Token -> {
                        if (firstTokenTime == null) {
                            firstTokenTime = System.currentTimeMillis()
                            val ttft = firstTokenTime!! - genStart
                            Log.i(TAG, "TTFT: ${ttft}ms")
                        }
                        responseBuilder.append(result.text)
                        tokenCount++
                    }
                    is LlmStreamResult.Completed -> {
                        Log.i(TAG, "Generation completed")
                    }
                    is LlmStreamResult.Error -> {
                        Log.e(TAG, "ERROR: Generation failed - ${result.throwable}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Exception during generation", e)
        }

        val totalTime = System.currentTimeMillis() - genStart
        val response = responseBuilder.toString()

        // 9. Print results
        Log.i(TAG, "")
        Log.i(TAG, "===========================================")
        Log.i(TAG, "RESULTS")
        Log.i(TAG, "===========================================")
        Log.i(TAG, "Response: $response")
        Log.i(TAG, "")
        Log.i(TAG, "Tokens: $tokenCount")
        Log.i(TAG, "Total time: ${totalTime}ms")
        Log.i(TAG, "TTFT: ${(firstTokenTime ?: genStart) - genStart}ms")
        if (totalTime > 0) {
            Log.i(TAG, "Tokens/sec: ${tokenCount * 1000.0 / totalTime}")
        }
        Log.i(TAG, "===========================================")
    }
}

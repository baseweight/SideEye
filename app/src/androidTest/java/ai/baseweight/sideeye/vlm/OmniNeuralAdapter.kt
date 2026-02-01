package ai.baseweight.sideeye.vlm

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import ai.baseweight.sideeye.data.ai.ModelDownloader
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.LlmStreamResult
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.VlmChatMessage
import com.nexa.sdk.bean.VlmContent
import com.nexa.sdk.bean.VlmCreateInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Adapter for OmniNeural-4B VLM model via Nexa SDK on Qualcomm NPU.
 *
 * NOTE: This adapter can be extended to support other NPU models by providing
 * a manifest file with ModelName, ModelType, and PluginId. See ~/nexa-sdk for examples.
 *
 * ROADMAP: Support more models by implementing manifest-based loading for:
 * - Qwen3-VL-4B-Instruct (if NPU-optimized .nexa becomes available)
 * - Ministral-3-3B (if NPU-optimized .nexa becomes available)
 * - Other VLMs with NPU optimization
 */
class OmniNeuralAdapter : VlmModelAdapter {

    companion object {
        private const val TAG = "OmniNeuralAdapter"
        const val MODEL_NAME = "omni-neural"
    }

    override val modelName: String = "OmniNeural-4B"
    override val modelId: String = MODEL_NAME

    private var vlmWrapper: VlmWrapper? = null
    private var isInitialized = false
    private var tempImageDir: File? = null

    override suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing Nexa SDK...")
            NexaSdk.getInstance().init(context)

            Log.d(TAG, "Building VlmWrapper for OmniNeural...")

            // Create temp directory for image files
            tempImageDir = File(context.cacheDir, "vlm_temp_images").apply {
                if (!exists()) mkdirs()
            }

            // Get model directory from ModelDownloader
            val downloader = ModelDownloader(context)
            val modelDir = downloader.getModelDir(ModelDownloader.OMNINEURAL_MODEL_ID)

            if (!modelDir.exists()) {
                Log.e(TAG, "Model directory does not exist: ${modelDir.absolutePath}")
                Log.e(TAG, "Please download the model first using ModelDownloader.downloadOmniNeural()")
                isInitialized = false
                return@withContext false
            }

            val config = ModelConfig(
                nCtx = 2048,
                nThreads = 8,
                enable_thinking = false,
                npu_lib_folder_path = context.applicationInfo.nativeLibraryDir,
                npu_model_folder_path = modelDir.absolutePath
            )

            VlmWrapper.builder()
                .vlmCreateInput(
                    VlmCreateInput(
                        model_name = MODEL_NAME,
                        model_path = File(modelDir, "files-1-1.nexa").absolutePath,
                        mmproj_path = null, // NPU models don't need separate mmproj
                        config = config,
                        plugin_id = "npu"
                    )
                )
                .build()
                .onSuccess { wrapper ->
                    vlmWrapper = wrapper
                    isInitialized = true
                    Log.d(TAG, "OmniNeural model initialized successfully")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to initialize OmniNeural model", error)
                    isInitialized = false
                }

            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialization", e)
            isInitialized = false
            false
        }
    }

    override suspend fun analyze(bitmap: Bitmap, prompt: String): String = withContext(Dispatchers.IO) {
        check(isInitialized && vlmWrapper != null) { "Model not initialized. Call initialize() first." }

        try {
            // Save bitmap to temp file (VLM needs file path)
            val tempFile = File(tempImageDir, "temp_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val result = analyzeImageFile(tempFile.absolutePath, prompt)

            // Clean up temp file
            tempFile.delete()

            result
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            throw e
        }
    }

    override suspend fun analyze(context: Context, uri: Uri, prompt: String): String = withContext(Dispatchers.IO) {
        check(isInitialized && vlmWrapper != null) { "Model not initialized. Call initialize() first." }

        try {
            // Copy URI content to temp file
            val tempFile = File(tempImageDir, "temp_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Failed to open URI: $uri")

            val result = analyzeImageFile(tempFile.absolutePath, prompt)

            // Clean up temp file
            tempFile.delete()

            result
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            throw e
        }
    }

    private suspend fun analyzeImageFile(imagePath: String, prompt: String): String {
        val wrapper = vlmWrapper ?: throw IllegalStateException("VlmWrapper is null")

        // Build message with image and text content
        val contents = listOf(
            VlmContent("image", imagePath),
            VlmContent("text", prompt)
        )

        val message = VlmChatMessage(
            role = "user",
            contents = contents
        )

        val messageArray = arrayOf(message)

        // Apply chat template
        var formattedPrompt = prompt
        wrapper.applyChatTemplate(messageArray, null, false)
            .onSuccess { result ->
                formattedPrompt = result.formattedText
            }
            .onFailure { error ->
                Log.w(TAG, "Failed to apply chat template, using raw prompt", error)
            }

        // Inject media paths into generation config
        val baseConfig = com.nexa.sdk.bean.GenerationConfig()
        val configWithMedia = wrapper.injectMediaPathsToConfig(messageArray, baseConfig)

        // Collect streaming response
        val responseBuilder = StringBuilder()

        wrapper.generateStreamFlow(prompt, configWithMedia).collect { result ->
            when (result) {
                is LlmStreamResult.Token -> {
                    responseBuilder.append(result.text)
                }
                is LlmStreamResult.Completed -> {
                    Log.d(TAG, "Generation completed. Profile: ${result.profile}")
                }
                is LlmStreamResult.Error -> {
                    Log.e(TAG, "Generation error")
                    throw RuntimeException("VLM generation error")
                }
            }
        }

        return responseBuilder.toString()
    }

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Releasing OmniNeural model...")
                vlmWrapper?.let { wrapper ->
                    wrapper.stopStream()
                    wrapper.destroy()
                }
                vlmWrapper = null
                isInitialized = false

                // Clean up temp directory
                tempImageDir?.deleteRecursively()
                tempImageDir = null

                Log.d(TAG, "OmniNeural model released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing model", e)
            }
        }
    }

    override fun isReady(): Boolean = isInitialized && vlmWrapper != null
}

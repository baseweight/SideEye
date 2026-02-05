package ai.baseweight.sideeye.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.GenerationConfig
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
 * Image analyzer using OmniNeural-4B VLM for content moderation.
 * Runs on Qualcomm NPU for fast on-device inference.
 */
class ImageAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "ImageAnalyzer"
        private const val MODEL_NAME = "omni-neural"
        private const val TARGET_SIZE = 448 // Required for NPU VLM
        private const val REINIT_INTERVAL = 4 // Reinitialize model every N images to avoid NPU crashes

        // Prompt that works well with OmniNeural (from benchmark testing)
        private const val ANALYSIS_PROMPT = """Classify this image. Pick ONE category:

0 = Safe (nothing inappropriate)
1 = Nudity (visible breasts, genitals, buttocks)
2 = Suggestive (bikinis, swimwear, revealing clothing)
3 = Drugs (cannabis, marijuana, drug paraphernalia)
4 = Embarrassing (drunk person, vomiting)

First briefly describe what you see, then respond with the number.
Format: "I see [description]. Category: [number]"
Example: "I see a beach sunset. Category: 0"
Example: "I see cannabis on a scale. Category: 3" """
    }

    private var vlmWrapper: VlmWrapper? = null
    private var isInitialized = false
    private var tempImageDir: File? = null
    private var inferenceCount = 0

    /**
     * Initialize the VLM model. Must be called before analyze().
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && vlmWrapper != null) {
            return@withContext true
        }

        try {
            Log.d(TAG, "Initializing Nexa SDK...")
            NexaSdk.getInstance().init(context)

            // Create temp directory for image files
            tempImageDir = File(context.cacheDir, "analyzer_temp_images").apply {
                if (!exists()) mkdirs()
            }

            initializeModel()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialization", e)
            isInitialized = false
            false
        }
    }

    private suspend fun initializeModel(): Boolean {
        // Get model directory
        val downloader = ModelDownloader(context)
        val modelDir = downloader.getModelDir(ModelDownloader.OMNINEURAL_MODEL_ID)

        if (!modelDir.exists()) {
            Log.e(TAG, "Model not downloaded. Please download first.")
            return false
        }

        val config = ModelConfig(
            nCtx = 2048,
            nThreads = 8,
            enable_thinking = false,
            npu_lib_folder_path = context.applicationInfo.nativeLibraryDir,
            npu_model_folder_path = modelDir.absolutePath
        )

        var success = false
        VlmWrapper.builder()
            .vlmCreateInput(
                VlmCreateInput(
                    model_name = MODEL_NAME,
                    model_path = File(modelDir, "files-1-1.nexa").absolutePath,
                    mmproj_path = null,
                    config = config,
                    plugin_id = "npu"
                )
            )
            .build()
            .onSuccess { wrapper ->
                vlmWrapper = wrapper
                isInitialized = true
                inferenceCount = 0
                success = true
                Log.d(TAG, "ImageAnalyzer initialized successfully")
            }
            .onFailure { error ->
                Log.e(TAG, "Failed to initialize model", error)
                isInitialized = false
            }

        return success
    }

    /**
     * Release and reinitialize the model. Required every few images to avoid NPU crashes.
     */
    private suspend fun reinitializeModel() {
        Log.d(TAG, "Reinitializing model after $inferenceCount inferences...")
        vlmWrapper?.let { wrapper ->
            wrapper.stopStream()
            wrapper.destroy()
        }
        vlmWrapper = null
        isInitialized = false
        initializeModel()
    }

    /**
     * Check if the model is ready for inference.
     */
    fun isReady(): Boolean = isInitialized && vlmWrapper != null

    /**
     * Analyze an image for content moderation flags.
     */
    suspend fun analyze(
        imageUri: Uri,
        imageId: Long,
        enabledCategories: Set<FlagCategory> = FlagCategory.entries.toSet(),
        threshold: Float = 0.7f
    ): AnalysisResult = withContext(Dispatchers.IO) {
        check(isInitialized && vlmWrapper != null) { "Model not initialized. Call initialize() first." }

        // Reinitialize model periodically to avoid NPU crashes
        if (inferenceCount > 0 && inferenceCount % REINIT_INTERVAL == 0) {
            reinitializeModel()
        }

        val startTime = System.currentTimeMillis()

        try {
            // Copy URI to temp file and resize to 448x448
            val tempFile = File(tempImageDir, "analyze_${System.currentTimeMillis()}.jpg")
            prepareImage(imageUri, tempFile)

            // Run inference
            val response = runInference(tempFile.absolutePath)
            inferenceCount++

            // Clean up temp file
            tempFile.delete()

            // Parse response
            val (isFlagged, category) = parseResponse(response)

            val analysisTimeMs = System.currentTimeMillis() - startTime

            // Map category to FlagCategory
            val primaryFlag = category?.let { mapToFlagCategory(it) }

            // Check if the detected category is enabled
            val effectivelyFlagged = isFlagged && (primaryFlag == null || primaryFlag in enabledCategories)

            // Build flags map
            val flags = mutableMapOf<FlagCategory, Float>()
            if (primaryFlag != null && effectivelyFlagged) {
                flags[primaryFlag] = 1.0f // Model gives binary classification
            }

            AnalysisResult(
                imageId = imageId,
                flags = flags,
                isFlagged = effectivelyFlagged,
                primaryFlag = if (effectivelyFlagged) primaryFlag else null,
                primaryConfidence = if (effectivelyFlagged) 1.0f else 0f,
                analysisTimeMs = analysisTimeMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed", e)
            val analysisTimeMs = System.currentTimeMillis() - startTime
            AnalysisResult(
                imageId = imageId,
                flags = emptyMap(),
                isFlagged = false,
                primaryFlag = null,
                primaryConfidence = 0f,
                analysisTimeMs = analysisTimeMs
            )
        }
    }

    /**
     * Prepare image: copy from URI and resize to 448x448 for NPU compatibility.
     */
    private fun prepareImage(uri: Uri, destFile: File) {
        // Load bitmap from URI
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Failed to open URI: $uri")

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        // First pass to get dimensions
        inputStream.use { BitmapFactory.decodeStream(it, null, options) }

        // Calculate sample size for efficiency
        options.inSampleSize = calculateInSampleSize(options, TARGET_SIZE, TARGET_SIZE)
        options.inJustDecodeBounds = false

        // Reopen stream and decode
        val inputStream2 = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Failed to open URI: $uri")

        val bitmap = inputStream2.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("Failed to decode image: $uri")

        // Resize to exact target size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, TARGET_SIZE, TARGET_SIZE, true)
        if (resizedBitmap != bitmap) {
            bitmap.recycle()
        }

        // Save as JPEG
        FileOutputStream(destFile).use { out ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        resizedBitmap.recycle()

        Log.d(TAG, "Prepared image: ${destFile.absolutePath} (${TARGET_SIZE}x${TARGET_SIZE})")
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private suspend fun runInference(imagePath: String): String {
        val wrapper = vlmWrapper ?: throw IllegalStateException("VlmWrapper is null")

        // Build message with image and prompt
        val contents = listOf(
            VlmContent("image", imagePath),
            VlmContent("text", ANALYSIS_PROMPT)
        )

        val chatList = arrayListOf(VlmChatMessage("user", contents))
        val chatArray = chatList.toTypedArray()

        // Apply chat template
        val templateResult = wrapper.applyChatTemplate(chatArray, null, false)
        val formattedText = templateResult.getOrThrow().formattedText

        // Create config with imagePaths directly (required for NPU)
        val genConfig = GenerationConfig(
            maxTokens = 256,
            imagePaths = arrayOf(imagePath),
            imageCount = 1
        )

        // Collect streaming response
        val responseBuilder = StringBuilder()

        wrapper.generateStreamFlow(formattedText, genConfig).collect { result ->
            when (result) {
                is LlmStreamResult.Token -> {
                    responseBuilder.append(result.text)
                }
                is LlmStreamResult.Completed -> {
                    Log.d(TAG, "Generation completed")
                }
                is LlmStreamResult.Error -> {
                    Log.e(TAG, "Generation error: ${result.throwable}")
                    throw RuntimeException("VLM generation error: ${result.throwable}")
                }
            }
        }

        // Clear pending state
        wrapper.stopStream()

        return responseBuilder.toString()
    }

    /**
     * Parse response in "Category: X" format.
     * Returns (isFlagged, categoryName)
     */
    private fun parseResponse(response: String): Pair<Boolean, String?> {
        val categoryRegex = """Category:\s*(\d)""".toRegex(RegexOption.IGNORE_CASE)
        val match = categoryRegex.find(response)

        if (match != null) {
            val category = match.groupValues[1].toIntOrNull() ?: 0
            return when (category) {
                0 -> Pair(false, null)  // Safe
                1 -> Pair(true, "Nudity")
                2 -> Pair(true, "Suggestive")
                3 -> Pair(true, "Drugs")
                4 -> Pair(true, "Embarrassing")
                else -> Pair(false, null)
            }
        }

        Log.w(TAG, "Could not parse category from response: $response")
        return Pair(false, null)
    }

    private fun mapToFlagCategory(categoryName: String): FlagCategory? {
        return when (categoryName.uppercase()) {
            "NUDITY" -> FlagCategory.NUDITY
            "SUGGESTIVE" -> FlagCategory.SUGGESTIVE
            "DRUGS" -> FlagCategory.DRUGS
            "EMBARRASSING" -> FlagCategory.EMBARRASSING
            else -> null
        }
    }

    /**
     * Release the model and clean up resources.
     */
    suspend fun release() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Releasing ImageAnalyzer...")
                vlmWrapper?.let { wrapper ->
                    wrapper.stopStream()
                    wrapper.destroy()
                }
                vlmWrapper = null
                isInitialized = false
                inferenceCount = 0

                tempImageDir?.deleteRecursively()
                tempImageDir = null

                Log.d(TAG, "ImageAnalyzer released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing model", e)
            }
        }
    }
}

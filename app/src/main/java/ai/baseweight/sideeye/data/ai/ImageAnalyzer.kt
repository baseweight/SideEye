package ai.baseweight.sideeye.data.ai

import android.content.Context
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

        // Prompt for content analysis
        private const val ANALYSIS_PROMPT = """Analyze this image for content moderation. Rate each category from 0 to 100 based on how likely the image contains that type of content. Use exactly this format:

NUDITY: [score]
DRINKING: [score]
DRUGS: [score]
EMBARRASSING: [score]

Where [score] is a number from 0-100. 0 means definitely not present, 100 means definitely present.

- NUDITY: exposed skin, revealing clothing, intimate content
- DRINKING: alcohol bottles, glasses, drinking scenes
- DRUGS: drug paraphernalia, pills, smoking materials
- EMBARRASSING: unflattering poses, messy appearance, awkward situations

Respond with ONLY the four lines in the exact format above."""
    }

    private var vlmWrapper: VlmWrapper? = null
    private var isInitialized = false
    private var tempImageDir: File? = null

    /**
     * Initialize the VLM model. Must be called before analyze().
     * This downloads the model if needed and loads it onto the NPU.
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

            // Get model directory
            val downloader = ModelDownloader(context)
            val modelDir = downloader.getModelDir(ModelDownloader.OMNINEURAL_MODEL_ID)

            if (!modelDir.exists()) {
                Log.e(TAG, "Model not downloaded. Please download first.")
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
                        mmproj_path = null,
                        config = config,
                        plugin_id = "npu"
                    )
                )
                .build()
                .onSuccess { wrapper ->
                    vlmWrapper = wrapper
                    isInitialized = true
                    Log.d(TAG, "ImageAnalyzer initialized successfully")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to initialize model", error)
                    isInitialized = false
                }

            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialization", e)
            isInitialized = false
            false
        }
    }

    /**
     * Check if the model is ready for inference.
     */
    fun isReady(): Boolean = isInitialized && vlmWrapper != null

    /**
     * Analyze an image for content moderation flags.
     *
     * @param imageUri URI of the image to analyze
     * @param imageId Unique ID for this image (used in result)
     * @param enabledCategories Which categories to check (scores for disabled categories will be 0)
     * @param threshold Minimum confidence to flag (0.0-1.0)
     * @return AnalysisResult with confidence scores for each category
     */
    suspend fun analyze(
        imageUri: Uri,
        imageId: Long,
        enabledCategories: Set<FlagCategory> = FlagCategory.entries.toSet(),
        threshold: Float = 0.7f
    ): AnalysisResult = withContext(Dispatchers.IO) {
        check(isInitialized && vlmWrapper != null) { "Model not initialized. Call initialize() first." }

        val startTime = System.currentTimeMillis()

        try {
            // Copy URI to temp file
            val tempFile = File(tempImageDir, "analyze_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Failed to open URI: $imageUri")

            // Run inference
            val response = runInference(tempFile.absolutePath, ANALYSIS_PROMPT)

            // Clean up temp file
            tempFile.delete()

            // Parse response into scores
            val scores = parseResponse(response, enabledCategories)

            val analysisTimeMs = System.currentTimeMillis() - startTime

            // Determine if flagged and primary flag
            val flaggedScores = scores.filter { it.value >= threshold }
            val isFlagged = flaggedScores.isNotEmpty()
            val primaryEntry = scores.maxByOrNull { it.value }
            val primaryFlag = if (isFlagged) primaryEntry?.key else null
            val primaryConfidence = primaryEntry?.value ?: 0f

            AnalysisResult(
                imageId = imageId,
                flags = scores,
                isFlagged = isFlagged,
                primaryFlag = primaryFlag,
                primaryConfidence = primaryConfidence,
                analysisTimeMs = analysisTimeMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed", e)
            val analysisTimeMs = System.currentTimeMillis() - startTime
            // Return empty result on error
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

    private suspend fun runInference(imagePath: String, prompt: String): String {
        val wrapper = vlmWrapper ?: throw IllegalStateException("VlmWrapper is null")

        val contents = listOf(
            VlmContent("image", imagePath),
            VlmContent("text", prompt)
        )

        val message = VlmChatMessage(
            role = "user",
            contents = contents
        )

        val messageArray = arrayOf(message)

        // Inject media paths into generation config
        val baseConfig = GenerationConfig()
        val configWithMedia = wrapper.injectMediaPathsToConfig(messageArray, baseConfig)

        // Collect streaming response
        val responseBuilder = StringBuilder()

        wrapper.generateStreamFlow(prompt, configWithMedia).collect { result ->
            when (result) {
                is LlmStreamResult.Token -> {
                    responseBuilder.append(result.text)
                }
                is LlmStreamResult.Completed -> {
                    Log.d(TAG, "Generation completed")
                }
                is LlmStreamResult.Error -> {
                    Log.e(TAG, "Generation error")
                    throw RuntimeException("VLM generation error")
                }
            }
        }

        return responseBuilder.toString()
    }

    private fun parseResponse(
        response: String,
        enabledCategories: Set<FlagCategory>
    ): Map<FlagCategory, Float> {
        val scores = mutableMapOf<FlagCategory, Float>()

        // Initialize all enabled categories to 0
        enabledCategories.forEach { scores[it] = 0f }

        // Parse each line for scores
        val lines = response.uppercase().lines()

        for (category in enabledCategories) {
            val categoryName = category.name
            val matchingLine = lines.find { it.startsWith("$categoryName:") }

            if (matchingLine != null) {
                // Extract number from line like "NUDITY: 85" or "NUDITY: [85]"
                val numberMatch = Regex("""[\[\s]*(\d+)[\]\s]*""").find(
                    matchingLine.substringAfter(":")
                )
                val scoreValue = numberMatch?.groupValues?.get(1)?.toFloatOrNull()

                if (scoreValue != null) {
                    // Convert 0-100 to 0.0-1.0
                    scores[category] = (scoreValue / 100f).coerceIn(0f, 1f)
                }
            }
        }

        Log.d(TAG, "Parsed scores: $scores from response: $response")
        return scores
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

                tempImageDir?.deleteRecursively()
                tempImageDir = null

                Log.d(TAG, "ImageAnalyzer released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing model", e)
            }
        }
    }
}

package ai.baseweight.sideeye.vlm

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * Interface for VLM model adapters.
 * Implementations wrap specific models (OmniNeural, Qwen3-VL) with a common API.
 */
interface VlmModelAdapter {

    /** Human-readable model name for reporting */
    val modelName: String

    /** Model identifier used by ai.nexa:core */
    val modelId: String

    /**
     * Initialize the model. Call before any inference.
     * @return true if initialization succeeded
     */
    suspend fun initialize(context: Context): Boolean

    /**
     * Run inference on an image with a prompt.
     * @param bitmap The image to analyze
     * @param prompt The prompt/question to ask about the image
     * @return Model response text
     */
    suspend fun analyze(bitmap: Bitmap, prompt: String): String

    /**
     * Run inference on an image URI with a prompt.
     * @param context Context for content resolver
     * @param uri URI of the image
     * @param prompt The prompt/question to ask about the image
     * @return Model response text
     */
    suspend fun analyze(context: Context, uri: Uri, prompt: String): String

    /**
     * Release model resources. Call when done with evaluation.
     */
    suspend fun release()

    /**
     * Check if model is currently loaded and ready.
     */
    fun isReady(): Boolean
}

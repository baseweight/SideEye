package ai.baseweight.sideeye.vlm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Comprehensive OmniNeural benchmark matching the Python benchmark format.
 *
 * Setup:
 * 1. Push test images: adb push ~/test_photo_set /sdcard/Android/data/ai.baseweight.sideeye/files/test_photo_set
 * 2. Run: adb shell am instrument -w -e class ai.baseweight.sideeye.vlm.OmniNeuralBenchmark ai.baseweight.sideeye.test/androidx.test.runner.AndroidJUnitRunner
 * 3. Pull results: adb pull /sdcard/Android/data/ai.baseweight.sideeye/files/results_omnineural.json
 */
@RunWith(AndroidJUnit4::class)
class OmniNeuralBenchmark {

    companion object {
        private const val TAG = "OmniNeuralBenchmark"
        private const val MODEL_NAME = "omni-neural"

        // Same prompt as Python benchmark - with <image> token for VLM
        private const val PROMPT = """<image>
You are a content moderation system. Classify this image.

FLAG if it contains:
- Sexual: Sex toys, explicit content, pornography
- Nudity: Visible breasts or genitals (including medical/art contexts)
- Suggestive: Revealing clothing, provocative poses, swimwear, exposed skin
- Drugs: Cannabis, paraphernalia
- Embarrassing: Visibly intoxicated/drunk people, vomiting, passed out, people in humiliating or compromising situations

Respond ONLY with JSON: {"flag": true, "reason": "Nudity"} or {"flag": false, "reason": null}"""

        private val VALID_REASONS = setOf("Sexual", "Nudity", "Suggestive", "Drugs", "Embarrassing")
    }

    private lateinit var context: android.content.Context
    private lateinit var downloader: ModelDownloader
    private lateinit var testDir: File
    private lateinit var outputFile: File

    private var vlmWrapper: VlmWrapper? = null
    private var tempImageDir: File? = null

    // Detailed metrics
    data class InferenceMetrics(
        val timeToFirstTokenMs: Long,
        val totalTokens: Int,
        val totalInferenceTimeMs: Long,
        val tokensPerSecond: Double
    )

    data class ImageResult(
        val filename: String,
        val groundTruthFlag: Boolean,
        val groundTruthReason: String?,
        val predictedFlag: Boolean,
        val predictedReason: String?,
        val correctFlag: Boolean,
        val correctReason: Boolean,
        val rawResponse: String,
        val parseError: Boolean,
        val inferenceTimeMs: Long,
        val timeToFirstTokenMs: Long,
        val totalTokens: Int,
        val tokensPerSecond: Double
    )

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        downloader = ModelDownloader(context)
        testDir = File(context.getExternalFilesDir(null), "test_photo_set")
        outputFile = File(context.getExternalFilesDir(null), "results_omnineural.json")

        // Use external files dir for temp images - cache dir may not be accessible to NPU
        tempImageDir = File(context.getExternalFilesDir(null), "benchmark_temp").apply {
            if (!exists()) mkdirs()
        }

        Log.i(TAG, "Test directory: ${testDir.absolutePath}")
        Log.i(TAG, "Output file: ${outputFile.absolutePath}")
        Log.i(TAG, "Temp directory: ${tempImageDir?.absolutePath}")
    }

    @After
    fun teardown() {
        runBlocking {
            releaseModel()
        }
        tempImageDir?.deleteRecursively()
    }

    private suspend fun initializeModel(): Boolean = withContext(Dispatchers.IO) {
        if (vlmWrapper != null) return@withContext true

        try {
            Log.i(TAG, "Initializing Nexa SDK...")
            NexaSdk.getInstance().init(context)

            val modelDir = downloader.getModelDir(ModelDownloader.OMNINEURAL_MODEL_ID)
            if (!modelDir.exists()) {
                Log.e(TAG, "Model not downloaded!")
                return@withContext false
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
                    success = true
                    Log.i(TAG, "Model initialized successfully")
                }
                .onFailure { error ->
                    Log.e(TAG, "Model initialization failed", error)
                }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialization", e)
            false
        }
    }

    private suspend fun releaseModel() = withContext(Dispatchers.IO) {
        vlmWrapper?.let { wrapper ->
            wrapper.stopStream()
            wrapper.destroy()
        }
        vlmWrapper = null
    }

    private suspend fun runInference(imagePath: String): Pair<String, InferenceMetrics> = withContext(Dispatchers.IO) {
        val wrapper = vlmWrapper ?: throw IllegalStateException("Model not initialized")

        Log.i(TAG, "runInference called with imagePath: $imagePath")
        Log.i(TAG, "Image file exists: ${File(imagePath).exists()}")

        // Build message with image and prompt
        val contents = listOf(
            VlmContent("image", imagePath),
            VlmContent("text", PROMPT)
        )

        Log.i(TAG, "VlmContent image path: ${contents[0].text}")

        val chatList = arrayListOf(VlmChatMessage("user", contents))
        val chatArray = chatList.toTypedArray()

        // Apply chat template to format the prompt (messages, tools, enableThinking)
        val templateResult = wrapper.applyChatTemplate(chatArray, null, false)
        val formattedText = templateResult.getOrThrow().formattedText
        Log.d(TAG, "Formatted text length: ${formattedText.length}")
        Log.i(TAG, "=== FULL FORMATTED TEXT START ===")
        Log.i(TAG, formattedText)
        Log.i(TAG, "=== FULL FORMATTED TEXT END ===")

        // Create config with media paths injected
        val baseConfig = GenerationConfig(maxTokens = 256)
        val configWithMedia = wrapper.injectMediaPathsToConfig(chatArray, baseConfig)
        Log.i(TAG, "Config imagePaths: ${configWithMedia.imagePaths?.joinToString()}")
        Log.i(TAG, "Config imageCount: ${configWithMedia.imageCount}")

        val responseBuilder = StringBuilder()
        var firstTokenTime: Long? = null
        var tokenCount = 0
        val startTime = System.currentTimeMillis()

        wrapper.generateStreamFlow(formattedText, configWithMedia).collect { result ->
            when (result) {
                is LlmStreamResult.Token -> {
                    if (firstTokenTime == null) {
                        firstTokenTime = System.currentTimeMillis()
                    }
                    responseBuilder.append(result.text)
                    tokenCount++
                }
                is LlmStreamResult.Completed -> {
                    Log.d(TAG, "Generation completed. Profile: ${result.profile}")
                }
                is LlmStreamResult.Error -> {
                    throw RuntimeException("VLM generation error: ${result.throwable}")
                }
            }
        }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val ttft = (firstTokenTime ?: endTime) - startTime
        val tps = if (totalTime > 0) tokenCount * 1000.0 / totalTime else 0.0

        val metrics = InferenceMetrics(
            timeToFirstTokenMs = ttft,
            totalTokens = tokenCount,
            totalInferenceTimeMs = totalTime,
            tokensPerSecond = tps
        )

        Pair(responseBuilder.toString(), metrics)
    }

    private fun parseResponse(response: String): Pair<Boolean, String?> {
        val jsonRegex = """\{[^}]+\}""".toRegex()
        val match = jsonRegex.find(response) ?: return Pair(false, null)

        return try {
            val json = JSONObject(match.value)
            val flag = when (val f = json.opt("flag")) {
                is Boolean -> f
                is String -> f.lowercase() in listOf("true", "yes", "1")
                else -> false
            }
            var reason = json.optString("reason", null)
            if (reason != null && reason !in VALID_REASONS) {
                reason = null
            }
            Pair(flag, reason)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse response: $response", e)
            Pair(false, null)
        }
    }

    @Test
    fun runFullBenchmark(): Unit = runBlocking {
        Log.i(TAG, "=== Starting OmniNeural Full Benchmark ===")

        // Check test directory
        if (!testDir.exists()) {
            Log.e(TAG, "Test directory not found: ${testDir.absolutePath}")
            Log.e(TAG, "Push test images with: adb push ~/test_photo_set ${context.getExternalFilesDir(null)?.absolutePath}")
            return@runBlocking
        }

        // Load classifications
        val classificationsFile = File(testDir, "classifications.json")
        if (!classificationsFile.exists()) {
            Log.e(TAG, "classifications.json not found")
            return@runBlocking
        }

        val classificationsJson = JSONObject(classificationsFile.readText())
        val imagesArray = classificationsJson.getJSONArray("images")

        Log.i(TAG, "Found ${imagesArray.length()} images in dataset")

        // Ensure model is downloaded
        if (!downloader.isModelDownloaded(ModelDownloader.OMNINEURAL_MODEL_ID)) {
            Log.e(TAG, "Model not downloaded. Run ModelDownloadTest first.")
            return@runBlocking
        }

        // Initialize model
        val initStartTime = System.currentTimeMillis()
        if (!initializeModel()) {
            Log.e(TAG, "Failed to initialize model")
            return@runBlocking
        }
        val initTimeMs = System.currentTimeMillis() - initStartTime
        Log.i(TAG, "Model initialized in ${initTimeMs}ms")

        // Run benchmark
        val results = mutableListOf<ImageResult>()
        var totalCorrectFlag = 0
        var truePositives = 0
        var trueNegatives = 0
        var falsePositives = 0
        var falseNegatives = 0
        var parseErrors = 0
        var totalInferenceTimeMs = 0L
        var totalTtftMs = 0L
        var totalTokens = 0
        val byReason = mutableMapOf<String, MutableMap<String, Int>>()

        VALID_REASONS.forEach { reason ->
            byReason[reason] = mutableMapOf("tp" to 0, "fp" to 0, "fn" to 0)
        }

        val benchmarkStartTime = System.currentTimeMillis()

        for (i in 0 until imagesArray.length()) {
            val item = imagesArray.getJSONObject(i)
            val filename = item.getString("filename")
            val gtFlag = item.getBoolean("should_flag")
            val gtReason = item.optString("flag_reason", null)

            val imageFile = File(testDir, filename)
            if (!imageFile.exists()) {
                Log.w(TAG, "[${ i + 1}/${imagesArray.length()}] SKIP $filename (not found)")
                continue
            }

            // Convert to JPG for NPU compatibility
            val tempFile = File(tempImageDir, "temp_${System.currentTimeMillis()}.jpg")
            if (!convertToJpg(imageFile, tempFile)) {
                Log.e(TAG, "[${ i + 1}/${imagesArray.length()}] SKIP $filename (conversion failed)")
                continue
            }

            try {
                val (response, metrics) = runInference(tempFile.absolutePath)
                val (predFlag, predReason) = parseResponse(response)
                val isParsed = response.contains("{") && response.contains("}")

                val correctFlag = predFlag == gtFlag
                val correctReason = if (gtFlag) predReason == gtReason else true

                // Update metrics
                if (correctFlag) totalCorrectFlag++
                if (!isParsed) parseErrors++
                totalInferenceTimeMs += metrics.totalInferenceTimeMs
                totalTtftMs += metrics.timeToFirstTokenMs
                totalTokens += metrics.totalTokens

                // Confusion matrix
                when {
                    gtFlag && predFlag -> truePositives++
                    !gtFlag && !predFlag -> trueNegatives++
                    !gtFlag && predFlag -> falsePositives++
                    gtFlag && !predFlag -> falseNegatives++
                }

                // Per-reason metrics
                if (gtReason != null) {
                    if (predFlag && predReason == gtReason) {
                        byReason[gtReason]!!["tp"] = byReason[gtReason]!!["tp"]!! + 1
                    } else {
                        byReason[gtReason]!!["fn"] = byReason[gtReason]!!["fn"]!! + 1
                    }
                }
                if (predReason != null && predReason != gtReason) {
                    byReason[predReason]!!["fp"] = byReason[predReason]!!["fp"]!! + 1
                }

                val result = ImageResult(
                    filename = filename,
                    groundTruthFlag = gtFlag,
                    groundTruthReason = gtReason,
                    predictedFlag = predFlag,
                    predictedReason = predReason,
                    correctFlag = correctFlag,
                    correctReason = correctReason,
                    rawResponse = response,
                    parseError = !isParsed,
                    inferenceTimeMs = metrics.totalInferenceTimeMs,
                    timeToFirstTokenMs = metrics.timeToFirstTokenMs,
                    totalTokens = metrics.totalTokens,
                    tokensPerSecond = metrics.tokensPerSecond
                )
                results.add(result)

                val status = if (correctFlag) "OK" else "WRONG"
                Log.i(TAG, "[${i + 1}/${imagesArray.length()}] $status $filename " +
                        "(gt=$gtFlag, pred=$predFlag, ${metrics.totalInferenceTimeMs}ms, " +
                        "TTFT=${metrics.timeToFirstTokenMs}ms, ${metrics.tokensPerSecond.format(1)} tok/s)")

            } catch (e: Exception) {
                Log.e(TAG, "[${i + 1}/${imagesArray.length()}] ERROR $filename", e)
                results.add(ImageResult(
                    filename = filename,
                    groundTruthFlag = gtFlag,
                    groundTruthReason = gtReason,
                    predictedFlag = false,
                    predictedReason = null,
                    correctFlag = false == gtFlag,
                    correctReason = false,
                    rawResponse = "ERROR: ${e.message}",
                    parseError = true,
                    inferenceTimeMs = 0,
                    timeToFirstTokenMs = 0,
                    totalTokens = 0,
                    tokensPerSecond = 0.0
                ))
            } finally {
                tempFile.delete()
            }
        }

        val benchmarkEndTime = System.currentTimeMillis()
        val totalBenchmarkTimeMs = benchmarkEndTime - benchmarkStartTime

        // Calculate final metrics
        val total = results.size
        val accuracy = if (total > 0) totalCorrectFlag.toDouble() / total else 0.0
        val precision = if (truePositives + falsePositives > 0) {
            truePositives.toDouble() / (truePositives + falsePositives)
        } else 0.0
        val recall = if (truePositives + falseNegatives > 0) {
            truePositives.toDouble() / (truePositives + falseNegatives)
        } else 0.0
        val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
        val fpr = if (falsePositives + trueNegatives > 0) {
            falsePositives.toDouble() / (falsePositives + trueNegatives)
        } else 0.0
        val fnr = if (falseNegatives + truePositives > 0) {
            falseNegatives.toDouble() / (falseNegatives + truePositives)
        } else 0.0

        val avgTimePerImage = if (total > 0) totalInferenceTimeMs.toDouble() / total else 0.0
        val avgTtft = if (total > 0) totalTtftMs.toDouble() / total else 0.0
        val avgTps = if (totalInferenceTimeMs > 0) totalTokens * 1000.0 / totalInferenceTimeMs else 0.0

        // Build output JSON
        val outputJson = JSONObject().apply {
            put("model_type", "omnineural")
            put("model_id", "NexaAI/OmniNeural-4B-mobile")
            put("metrics", JSONObject().apply {
                put("total", total)
                put("accuracy", accuracy)
                put("precision", precision)
                put("recall", recall)
                put("f1", f1)
                put("false_positive_rate", fpr)
                put("false_negative_rate", fnr)
                put("parse_errors", parseErrors)
                put("total_time_seconds", totalBenchmarkTimeMs / 1000.0)
                put("avg_time_per_image", avgTimePerImage / 1000.0)
                put("model_init_time_ms", initTimeMs)
                put("avg_ttft_ms", avgTtft)
                put("avg_tokens_per_second", avgTps)
                put("total_tokens", totalTokens)
            })
            put("confusion_matrix", JSONObject().apply {
                put("true_positives", truePositives)
                put("true_negatives", trueNegatives)
                put("false_positives", falsePositives)
                put("false_negatives", falseNegatives)
            })
            put("by_reason", JSONObject().apply {
                byReason.forEach { (reason, counts) ->
                    put(reason, JSONObject().apply {
                        counts.forEach { (k, v) -> put(k, v) }
                    })
                }
            })
            put("results", JSONArray().apply {
                results.forEach { r ->
                    put(JSONObject().apply {
                        put("filename", r.filename)
                        put("ground_truth_flag", r.groundTruthFlag)
                        put("ground_truth_reason", r.groundTruthReason)
                        put("predicted_flag", r.predictedFlag)
                        put("predicted_reason", r.predictedReason)
                        put("correct_flag", r.correctFlag)
                        put("correct_reason", r.correctReason)
                        put("raw_response", r.rawResponse)
                        put("parse_error", r.parseError)
                        put("inference_time", r.inferenceTimeMs / 1000.0)
                        put("ttft_ms", r.timeToFirstTokenMs)
                        put("tokens", r.totalTokens)
                        put("tokens_per_second", r.tokensPerSecond)
                    })
                }
            })
        }

        // Write results
        outputFile.writeText(outputJson.toString(2))
        Log.i(TAG, "Results written to: ${outputFile.absolutePath}")

        // Print summary
        Log.i(TAG, "")
        Log.i(TAG, "============================================================")
        Log.i(TAG, "RESULTS: OmniNeural-4B on Snapdragon NPU")
        Log.i(TAG, "============================================================")
        Log.i(TAG, "Accuracy:            ${(accuracy * 100).format(1)}% ($totalCorrectFlag/$total)")
        Log.i(TAG, "Precision:           ${(precision * 100).format(1)}%")
        Log.i(TAG, "Recall:              ${(recall * 100).format(1)}%")
        Log.i(TAG, "F1 Score:            ${(f1 * 100).format(1)}%")
        Log.i(TAG, "False Positive Rate: ${(fpr * 100).format(1)}%")
        Log.i(TAG, "False Negative Rate: ${(fnr * 100).format(1)}%")
        Log.i(TAG, "Parse Errors:        $parseErrors")
        Log.i(TAG, "")
        Log.i(TAG, "TIMING METRICS:")
        Log.i(TAG, "Model Init Time:     ${initTimeMs}ms")
        Log.i(TAG, "Total Benchmark:     ${totalBenchmarkTimeMs / 1000.0}s")
        Log.i(TAG, "Avg Time/Image:      ${(avgTimePerImage / 1000.0).format(2)}s")
        Log.i(TAG, "Avg TTFT:            ${avgTtft.format(0)}ms")
        Log.i(TAG, "Avg Tokens/Sec:      ${avgTps.format(1)}")
        Log.i(TAG, "Total Tokens:        $totalTokens")
        Log.i(TAG, "")
        Log.i(TAG, "Confusion Matrix:")
        Log.i(TAG, "  TP: $truePositives  FP: $falsePositives")
        Log.i(TAG, "  FN: $falseNegatives  TN: $trueNegatives")
        Log.i(TAG, "============================================================")

        println("\n=== BENCHMARK COMPLETE ===")
        println("Results: ${outputFile.absolutePath}")
        println("Pull with: adb pull ${outputFile.absolutePath}")
    }

    @Test
    fun quickTest(): Unit = runBlocking {
        Log.i(TAG, "=== Quick Test (3 images) ===")

        if (!testDir.exists()) {
            Log.e(TAG, "Test directory not found")
            return@runBlocking
        }

        val classificationsFile = File(testDir, "classifications.json")
        if (!classificationsFile.exists()) {
            Log.e(TAG, "classifications.json not found")
            return@runBlocking
        }

        val classificationsJson = JSONObject(classificationsFile.readText())
        val imagesArray = classificationsJson.getJSONArray("images")

        if (!initializeModel()) {
            Log.e(TAG, "Failed to initialize model")
            return@runBlocking
        }

        // Test first 3 images
        val testCount = minOf(3, imagesArray.length())
        for (i in 0 until testCount) {
            val item = imagesArray.getJSONObject(i)
            val filename = item.getString("filename")
            val gtFlag = item.getBoolean("should_flag")

            val imageFile = File(testDir, filename)
            if (!imageFile.exists()) continue

            // Convert to JPG for NPU compatibility
            val tempFile = File(tempImageDir, "temp_${System.currentTimeMillis()}.jpg")
            if (!convertToJpg(imageFile, tempFile)) {
                Log.e(TAG, "Failed to convert $filename")
                continue
            }
            Log.i(TAG, "Using temp image: ${tempFile.absolutePath}")

            try {
                val (response, metrics) = runInference(tempFile.absolutePath)
                val (predFlag, predReason) = parseResponse(response)

                Log.i(TAG, "[$filename]")
                Log.i(TAG, "  Ground truth: flag=$gtFlag")
                Log.i(TAG, "  Prediction:   flag=$predFlag, reason=$predReason")
                Log.i(TAG, "  Time: ${metrics.totalInferenceTimeMs}ms, TTFT: ${metrics.timeToFirstTokenMs}ms")
                Log.i(TAG, "  Tokens: ${metrics.totalTokens}, TPS: ${metrics.tokensPerSecond.format(1)}")
                Log.i(TAG, "  Response: $response")
            } finally {
                tempFile.delete()
            }
        }

        Log.i(TAG, "=== Quick Test Complete ===")
    }

    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

    /**
     * Convert any image to JPG format and resize for NPU compatibility.
     * VLMs typically expect specific input sizes (e.g., 448x448, 336x336).
     */
    private fun convertToJpg(sourceFile: File, destFile: File): Boolean {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)

            // Calculate sample size to load a reasonably sized bitmap
            val targetSize = 448 // Standard VLM input size
            options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode image: ${sourceFile.absolutePath}")
                return false
            }

            // Resize to exact target size
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
            if (resizedBitmap != bitmap) {
                bitmap.recycle()
            }

            FileOutputStream(destFile).use { out ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            resizedBitmap.recycle()
            Log.d(TAG, "Converted ${sourceFile.name} to ${targetSize}x${targetSize} JPG: ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image: ${e.message}")
            false
        }
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
}

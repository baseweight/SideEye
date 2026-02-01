package ai.baseweight.sideeye.vlm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Metrics collected for a single inference run.
 */
data class InferenceMetrics(
    val imageId: String,
    val inferenceTimeMs: Long,
    val memoryBeforeMb: Long,
    val memoryAfterMb: Long,
    val memoryPeakMb: Long,
    val modelResponse: String,
    val expectedLabel: String?,
    val isCorrect: Boolean?
)

/**
 * Aggregated benchmark results for a model.
 */
data class BenchmarkResult(
    val modelName: String,
    val modelId: String,
    val deviceInfo: DeviceInfo,
    val initTimeMs: Long,
    val totalImages: Int,
    val successfulInferences: Int,
    val failedInferences: Int,
    val inferenceMetrics: List<InferenceMetrics>,
    val timestampMs: Long = System.currentTimeMillis()
) {
    // Computed statistics
    val avgInferenceTimeMs: Double
        get() = if (inferenceMetrics.isNotEmpty()) {
            inferenceMetrics.map { it.inferenceTimeMs }.average()
        } else 0.0

    val minInferenceTimeMs: Long
        get() = inferenceMetrics.minOfOrNull { it.inferenceTimeMs } ?: 0

    val maxInferenceTimeMs: Long
        get() = inferenceMetrics.maxOfOrNull { it.inferenceTimeMs } ?: 0

    val p50InferenceTimeMs: Long
        get() = percentile(50)

    val p90InferenceTimeMs: Long
        get() = percentile(90)

    val p99InferenceTimeMs: Long
        get() = percentile(99)

    val avgMemoryUsageMb: Double
        get() = if (inferenceMetrics.isNotEmpty()) {
            inferenceMetrics.map { it.memoryAfterMb - it.memoryBeforeMb }.average()
        } else 0.0

    val peakMemoryMb: Long
        get() = inferenceMetrics.maxOfOrNull { it.memoryPeakMb } ?: 0

    val accuracy: Double?
        get() {
            val scored = inferenceMetrics.filter { it.isCorrect != null }
            return if (scored.isNotEmpty()) {
                scored.count { it.isCorrect == true }.toDouble() / scored.size
            } else null
        }

    private fun percentile(p: Int): Long {
        if (inferenceMetrics.isEmpty()) return 0
        val sorted = inferenceMetrics.map { it.inferenceTimeMs }.sorted()
        val index = ((p / 100.0) * sorted.size).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("model_name", modelName)
            put("model_id", modelId)
            put("device_info", deviceInfo.toJson())
            put("init_time_ms", initTimeMs)
            put("total_images", totalImages)
            put("successful_inferences", successfulInferences)
            put("failed_inferences", failedInferences)
            put("avg_inference_time_ms", avgInferenceTimeMs)
            put("min_inference_time_ms", minInferenceTimeMs)
            put("max_inference_time_ms", maxInferenceTimeMs)
            put("p50_inference_time_ms", p50InferenceTimeMs)
            put("p90_inference_time_ms", p90InferenceTimeMs)
            put("p99_inference_time_ms", p99InferenceTimeMs)
            put("avg_memory_usage_mb", avgMemoryUsageMb)
            put("peak_memory_mb", peakMemoryMb)
            accuracy?.let { put("accuracy", it) }
            put("timestamp_ms", timestampMs)
            put("inference_details", JSONArray().apply {
                inferenceMetrics.forEach { m ->
                    put(JSONObject().apply {
                        put("image_id", m.imageId)
                        put("inference_time_ms", m.inferenceTimeMs)
                        put("memory_before_mb", m.memoryBeforeMb)
                        put("memory_after_mb", m.memoryAfterMb)
                        put("memory_peak_mb", m.memoryPeakMb)
                        put("response", m.modelResponse)
                        m.expectedLabel?.let { put("expected_label", it) }
                        m.isCorrect?.let { put("is_correct", it) }
                    })
                }
            })
        }
    }

    fun toSummaryString(): String {
        return buildString {
            appendLine("=" .repeat(60))
            appendLine("BENCHMARK RESULTS: $modelName")
            appendLine("=".repeat(60))
            appendLine("Device: ${deviceInfo.model} (${deviceInfo.soc})")
            appendLine("Android: ${deviceInfo.androidVersion}")
            appendLine("-".repeat(60))
            appendLine("Initialization: ${initTimeMs}ms")
            appendLine("Images: $totalImages (success: $successfulInferences, failed: $failedInferences)")
            appendLine("-".repeat(60))
            appendLine("LATENCY:")
            appendLine("  Average: %.2fms".format(avgInferenceTimeMs))
            appendLine("  Min: ${minInferenceTimeMs}ms")
            appendLine("  Max: ${maxInferenceTimeMs}ms")
            appendLine("  P50: ${p50InferenceTimeMs}ms")
            appendLine("  P90: ${p90InferenceTimeMs}ms")
            appendLine("  P99: ${p99InferenceTimeMs}ms")
            appendLine("-".repeat(60))
            appendLine("MEMORY:")
            appendLine("  Avg Usage: %.2fMB".format(avgMemoryUsageMb))
            appendLine("  Peak: ${peakMemoryMb}MB")
            accuracy?.let {
                appendLine("-".repeat(60))
                appendLine("ACCURACY: %.2f%%".format(it * 100))
            }
            appendLine("=".repeat(60))
        }
    }
}

/**
 * Device information for benchmark context.
 */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val soc: String,
    val androidVersion: String,
    val sdkInt: Int,
    val totalRamMb: Long,
    val availableRamMb: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("manufacturer", manufacturer)
            put("model", model)
            put("soc", soc)
            put("android_version", androidVersion)
            put("sdk_int", sdkInt)
            put("total_ram_mb", totalRamMb)
            put("available_ram_mb", availableRamMb)
        }
    }
}

/**
 * Test image with optional ground truth label.
 */
data class TestImage(
    val id: String,
    val uri: android.net.Uri,
    val expectedLabel: String? = null
)

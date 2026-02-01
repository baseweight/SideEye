package ai.baseweight.sideeye.vlm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Runs VLM benchmarks and collects metrics.
 */
class VlmBenchmarkRunner(private val context: Context) {

    companion object {
        private const val TAG = "VlmBenchmarkRunner"
    }

    /**
     * Run a full benchmark suite for a model adapter.
     *
     * @param adapter The VLM model adapter to benchmark
     * @param testImages List of test images with optional labels
     * @param prompt The prompt to use for all inferences
     * @param warmupRuns Number of warmup runs before measuring (default 2)
     * @param labelMatcher Optional function to determine if response matches expected label
     * @return BenchmarkResult with all metrics
     */
    suspend fun runBenchmark(
        adapter: VlmModelAdapter,
        testImages: List<TestImage>,
        prompt: String,
        warmupRuns: Int = 2,
        labelMatcher: ((response: String, expectedLabel: String) -> Boolean)? = null
    ): BenchmarkResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting benchmark for ${adapter.modelName}")
        Log.i(TAG, "Test images: ${testImages.size}, Warmup runs: $warmupRuns")

        val deviceInfo = collectDeviceInfo()
        Log.i(TAG, "Device: ${deviceInfo.model} (${deviceInfo.soc})")

        // Initialize model and measure init time
        val initStartTime = System.currentTimeMillis()
        val initSuccess = adapter.initialize(context)
        val initTimeMs = System.currentTimeMillis() - initStartTime

        if (!initSuccess) {
            Log.e(TAG, "Model initialization failed")
            return@withContext BenchmarkResult(
                modelName = adapter.modelName,
                modelId = adapter.modelId,
                deviceInfo = deviceInfo,
                initTimeMs = initTimeMs,
                totalImages = testImages.size,
                successfulInferences = 0,
                failedInferences = testImages.size,
                inferenceMetrics = emptyList()
            )
        }

        Log.i(TAG, "Model initialized in ${initTimeMs}ms")

        // Warmup runs (not measured)
        if (warmupRuns > 0 && testImages.isNotEmpty()) {
            Log.i(TAG, "Running $warmupRuns warmup iterations...")
            val warmupImage = testImages.first()
            repeat(warmupRuns) { i ->
                try {
                    adapter.analyze(context, warmupImage.uri, prompt)
                    Log.d(TAG, "Warmup run ${i + 1}/$warmupRuns complete")
                } catch (e: Exception) {
                    Log.w(TAG, "Warmup run ${i + 1} failed", e)
                }
            }
        }

        // Run measured inferences
        val inferenceMetrics = mutableListOf<InferenceMetrics>()
        var successCount = 0
        var failCount = 0

        testImages.forEachIndexed { index, testImage ->
            Log.d(TAG, "Processing image ${index + 1}/${testImages.size}: ${testImage.id}")

            try {
                // Force GC before measurement for more consistent memory readings
                System.gc()
                Thread.sleep(100)

                val memoryBefore = getUsedMemoryMb()
                var memoryPeak = memoryBefore

                val startTime = System.currentTimeMillis()
                val response = adapter.analyze(context, testImage.uri, prompt)
                val inferenceTime = System.currentTimeMillis() - startTime

                val memoryAfter = getUsedMemoryMb()
                memoryPeak = maxOf(memoryPeak, memoryAfter)

                // Determine correctness if label matching is available
                val isCorrect = if (testImage.expectedLabel != null && labelMatcher != null) {
                    labelMatcher(response, testImage.expectedLabel)
                } else null

                inferenceMetrics.add(
                    InferenceMetrics(
                        imageId = testImage.id,
                        inferenceTimeMs = inferenceTime,
                        memoryBeforeMb = memoryBefore,
                        memoryAfterMb = memoryAfter,
                        memoryPeakMb = memoryPeak,
                        modelResponse = response,
                        expectedLabel = testImage.expectedLabel,
                        isCorrect = isCorrect
                    )
                )
                successCount++

                Log.d(TAG, "Image ${testImage.id}: ${inferenceTime}ms, correct=$isCorrect")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process image ${testImage.id}", e)
                failCount++

                inferenceMetrics.add(
                    InferenceMetrics(
                        imageId = testImage.id,
                        inferenceTimeMs = -1,
                        memoryBeforeMb = 0,
                        memoryAfterMb = 0,
                        memoryPeakMb = 0,
                        modelResponse = "ERROR: ${e.message}",
                        expectedLabel = testImage.expectedLabel,
                        isCorrect = false
                    )
                )
            }
        }

        // Release model
        adapter.release()

        val result = BenchmarkResult(
            modelName = adapter.modelName,
            modelId = adapter.modelId,
            deviceInfo = deviceInfo,
            initTimeMs = initTimeMs,
            totalImages = testImages.size,
            successfulInferences = successCount,
            failedInferences = failCount,
            inferenceMetrics = inferenceMetrics
        )

        Log.i(TAG, "Benchmark complete for ${adapter.modelName}")
        Log.i(TAG, result.toSummaryString())

        result
    }

    /**
     * Run benchmarks for multiple models and return comparative results.
     */
    suspend fun runComparativeBenchmark(
        adapters: List<VlmModelAdapter>,
        testImages: List<TestImage>,
        prompt: String,
        warmupRuns: Int = 2,
        labelMatcher: ((response: String, expectedLabel: String) -> Boolean)? = null
    ): List<BenchmarkResult> {
        return adapters.map { adapter ->
            runBenchmark(adapter, testImages, prompt, warmupRuns, labelMatcher)
        }
    }

    private fun collectDeviceInfo(): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            soc = getSocInfo(),
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            totalRamMb = memInfo.totalMem / (1024 * 1024),
            availableRamMb = memInfo.availMem / (1024 * 1024)
        )
    }

    private fun getSocInfo(): String {
        // Try to get SoC info from various sources
        return try {
            // Check for Qualcomm SoC
            val socModel = Build.HARDWARE
            val board = Build.BOARD

            // Try to read from /proc/cpuinfo for more detail
            val cpuInfo = readCpuInfo()
            val hardware = cpuInfo["Hardware"] ?: cpuInfo["model name"] ?: ""

            when {
                hardware.isNotEmpty() -> hardware
                socModel.contains("qcom", ignoreCase = true) -> "Qualcomm $board"
                else -> "$socModel ($board)"
            }
        } catch (e: Exception) {
            "${Build.HARDWARE} (${Build.BOARD})"
        }
    }

    private fun readCpuInfo(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/cpuinfo")).use { reader ->
                reader.lineSequence().forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        result[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read /proc/cpuinfo", e)
        }
        return result
    }

    private fun getUsedMemoryMb(): Long {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val nativeHeap = Debug.getNativeHeapAllocatedSize()
        return (usedMemory + nativeHeap) / (1024 * 1024)
    }
}

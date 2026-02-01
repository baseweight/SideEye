package ai.baseweight.sideeye.data.ai

data class AnalysisResult(
    val imageId: Long,
    val flags: Map<FlagCategory, Float>,  // category -> confidence 0.0-1.0
    val isFlagged: Boolean,
    val primaryFlag: FlagCategory?,
    val primaryConfidence: Float,
    val analysisTimeMs: Long
)

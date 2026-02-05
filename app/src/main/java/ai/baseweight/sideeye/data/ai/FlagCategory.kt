package ai.baseweight.sideeye.data.ai

enum class FlagCategory(val displayName: String, val description: String, val defaultEnabled: Boolean) {
    NUDITY("Nudity", "Photos containing nudity or revealing content", true),
    SUGGESTIVE("Suggestive", "Bikinis, swimwear, revealing clothing", true),
    DRUGS("Drugs", "Photos showing drug use or paraphernalia", true),
    EMBARRASSING("Embarrassing", "Potentially embarrassing or unflattering photos", false)
}

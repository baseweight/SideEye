package ai.baseweight.sideeye.data.ai

enum class FlagCategory(val displayName: String, val description: String, val defaultEnabled: Boolean) {
    NUDITY("Nudity", "Photos containing nudity or revealing content", true),
    DRINKING("Drinking", "Photos showing alcohol consumption", false),
    DRUGS("Drugs", "Photos showing drug use or paraphernalia", true),
    EMBARRASSING("Embarrassing", "Potentially embarrassing or unflattering photos", false)
}

package ai.baseweight.sideeye.data.ai

import android.content.Context
import ai.baseweight.sideeye.data.security.SecurityManager
import org.json.JSONArray
import org.json.JSONObject

data class ModerationSettings(
    val enabledCategories: Set<FlagCategory>,
    val sensitivityThreshold: Float = 0.7f,  // 0.0-1.0
    val autoScanNewPhotos: Boolean = false,
    val confirmBeforeDelete: Boolean = true
) {
    companion object {
        private const val KEY_MODERATION_SETTINGS = "moderation_settings"
        private const val KEY_ENABLED_CATEGORIES = "enabled_categories"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_AUTO_SCAN = "auto_scan"
        private const val KEY_CONFIRM_DELETE = "confirm_delete"

        fun getDefault(): ModerationSettings {
            return ModerationSettings(
                enabledCategories = FlagCategory.entries
                    .filter { it.defaultEnabled }
                    .toSet()
            )
        }

        fun load(context: Context): ModerationSettings {
            val prefs = SecurityManager.getEncryptedPrefs(context)
            val json = prefs.getString(KEY_MODERATION_SETTINGS, null)
                ?: return getDefault()

            return try {
                val obj = JSONObject(json)
                val categoriesArray = obj.optJSONArray(KEY_ENABLED_CATEGORIES)
                val enabledCategories = if (categoriesArray != null) {
                    (0 until categoriesArray.length())
                        .mapNotNull { i ->
                            try {
                                FlagCategory.valueOf(categoriesArray.getString(i))
                            } catch (e: Exception) {
                                null
                            }
                        }
                        .toSet()
                } else {
                    getDefault().enabledCategories
                }

                ModerationSettings(
                    enabledCategories = enabledCategories,
                    sensitivityThreshold = obj.optDouble(KEY_SENSITIVITY, 0.7).toFloat(),
                    autoScanNewPhotos = obj.optBoolean(KEY_AUTO_SCAN, false),
                    confirmBeforeDelete = obj.optBoolean(KEY_CONFIRM_DELETE, true)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                getDefault()
            }
        }

        fun save(context: Context, settings: ModerationSettings) {
            val prefs = SecurityManager.getEncryptedPrefs(context)
            val categoriesArray = JSONArray().apply {
                settings.enabledCategories.forEach { put(it.name) }
            }
            val obj = JSONObject().apply {
                put(KEY_ENABLED_CATEGORIES, categoriesArray)
                put(KEY_SENSITIVITY, settings.sensitivityThreshold.toDouble())
                put(KEY_AUTO_SCAN, settings.autoScanNewPhotos)
                put(KEY_CONFIRM_DELETE, settings.confirmBeforeDelete)
            }
            prefs.edit().putString(KEY_MODERATION_SETTINGS, obj.toString()).apply()
        }
    }
}

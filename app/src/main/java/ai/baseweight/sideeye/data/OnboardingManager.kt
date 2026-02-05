package ai.baseweight.sideeye.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages onboarding state persistence using EncryptedSharedPreferences.
 */
class OnboardingManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "sideeye_onboarding"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_MODEL_DOWNLOADED = "model_downloaded"
        private const val KEY_PERMISSIONS_GRANTED = "permissions_granted"
        private const val KEY_VAULT_SETUP = "vault_setup"
        private const val KEY_CATEGORIES_CONFIGURED = "categories_configured"
        private const val KEY_LAST_SCAN_TIMESTAMP = "last_scan_timestamp"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    fun isModelDownloaded(): Boolean = prefs.getBoolean(KEY_MODEL_DOWNLOADED, false)

    fun setModelDownloaded(downloaded: Boolean) {
        prefs.edit().putBoolean(KEY_MODEL_DOWNLOADED, downloaded).apply()
    }

    fun isPermissionsGranted(): Boolean = prefs.getBoolean(KEY_PERMISSIONS_GRANTED, false)

    fun setPermissionsGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_PERMISSIONS_GRANTED, granted).apply()
    }

    fun isVaultSetup(): Boolean = prefs.getBoolean(KEY_VAULT_SETUP, false)

    fun setVaultSetup(setup: Boolean) {
        prefs.edit().putBoolean(KEY_VAULT_SETUP, setup).apply()
    }

    fun isCategoriesConfigured(): Boolean = prefs.getBoolean(KEY_CATEGORIES_CONFIGURED, false)

    fun setCategoriesConfigured(configured: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORIES_CONFIGURED, configured).apply()
    }

    fun getLastScanTimestamp(): Long = prefs.getLong(KEY_LAST_SCAN_TIMESTAMP, 0L)

    fun setLastScanTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SCAN_TIMESTAMP, timestamp).apply()
    }

    /**
     * Mark the current time as the last scan timestamp.
     */
    fun updateLastScanTimestamp() {
        setLastScanTimestamp(System.currentTimeMillis())
    }

    /**
     * Reset all onboarding state (useful for testing or re-onboarding).
     */
    fun reset() {
        prefs.edit().clear().apply()
    }
}

package ai.baseweight.sideeye.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages security-related operations including MasterKey initialization
 * and encrypted preferences access for the vault feature.
 */
object SecurityManager {

    private const val VAULT_PREFS_FILE = "vault_encrypted_prefs"

    @Volatile
    private var masterKey: MasterKey? = null

    /**
     * Get or create the MasterKey backed by Android Keystore.
     * Uses AES256_GCM encryption scheme.
     */
    fun getMasterKey(context: Context): MasterKey {
        return masterKey ?: synchronized(this) {
            masterKey ?: createMasterKey(context).also { masterKey = it }
        }
    }

    private fun createMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * Get encrypted SharedPreferences for vault metadata and settings.
     */
    fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            VAULT_PREFS_FILE,
            getMasterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}

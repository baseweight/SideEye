package ai.baseweight.sideeye.data.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Manages vault PIN storage and verification using PBKDF2 hashing.
 * PIN is stored securely in EncryptedSharedPreferences.
 */
class VaultPinManager(private val context: Context) {

    private val prefs by lazy { SecurityManager.getEncryptedPrefs(context) }

    companion object {
        private const val KEY_PIN_HASH = "vault_pin_hash"
        private const val KEY_PIN_SALT = "vault_pin_salt"
        private const val KEY_PIN_SET = "vault_pin_is_set"
        private const val KEY_AUTH_METHOD = "vault_auth_method"
        private const val KEY_SESSION_TIMESTAMP = "vault_session_timestamp"
        private const val KEY_REQUIRE_AUTH_ON_LAUNCH = "vault_require_auth_on_launch"

        private const val PBKDF2_ITERATIONS = 100_000
        private const val HASH_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 32

        // Session timeout: 15 minutes in milliseconds
        private const val SESSION_TIMEOUT_MS = 15 * 60 * 1000L
    }

    /**
     * Check if a PIN has been set up for the vault.
     */
    fun isPinSet(): Boolean {
        return prefs.getBoolean(KEY_PIN_SET, false)
    }

    /**
     * Set up a new PIN for the vault.
     * @param pin The PIN to set (4-6 digits)
     * @return true if PIN was set successfully
     */
    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false

        val salt = generateSalt()
        val hash = hashPin(pin, salt)

        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putBoolean(KEY_PIN_SET, true)
            .apply()

        return true
    }

    /**
     * Verify a PIN against the stored hash.
     * @param pin The PIN to verify
     * @return true if PIN matches
     */
    fun verifyPin(pin: String): Boolean {
        if (!isPinSet()) return false

        val storedSalt = prefs.getString(KEY_PIN_SALT, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val storedHash = prefs.getString(KEY_PIN_HASH, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false

        val computedHash = hashPin(pin, storedSalt)
        return computedHash.contentEquals(storedHash)
    }

    /**
     * Change the existing PIN.
     * @param oldPin Current PIN for verification
     * @param newPin New PIN to set
     * @return true if PIN was changed successfully
     */
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        return setPin(newPin)
    }

    /**
     * Clear the PIN (removes vault authentication).
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .putBoolean(KEY_PIN_SET, false)
            .apply()
    }

    /**
     * Validate PIN format (4-6 digits).
     */
    fun isValidPin(pin: String): Boolean {
        return pin.length in 4..6 && pin.all { it.isDigit() }
    }

    /**
     * Get the current authentication method preference.
     */
    fun getAuthMethod(): AuthMethod {
        val value = prefs.getString(KEY_AUTH_METHOD, AuthMethod.BIOMETRIC.name)
        return try {
            AuthMethod.valueOf(value ?: AuthMethod.BIOMETRIC.name)
        } catch (e: IllegalArgumentException) {
            AuthMethod.BIOMETRIC
        }
    }

    /**
     * Set the authentication method preference.
     */
    fun setAuthMethod(method: AuthMethod) {
        prefs.edit().putString(KEY_AUTH_METHOD, method.name).apply()
    }

    /**
     * Check if authentication should be required on app launch.
     */
    fun isAuthRequiredOnLaunch(): Boolean {
        return prefs.getBoolean(KEY_REQUIRE_AUTH_ON_LAUNCH, false)
    }

    /**
     * Set whether authentication should be required on app launch.
     */
    fun setAuthRequiredOnLaunch(required: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_AUTH_ON_LAUNCH, required).apply()
    }

    /**
     * Update the session timestamp to current time.
     * Call this when user successfully authenticates or interacts with vault.
     */
    fun updateSessionTimestamp() {
        prefs.edit().putLong(KEY_SESSION_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    /**
     * Check if the current session is still valid (within timeout period).
     */
    fun isSessionValid(): Boolean {
        val lastTimestamp = prefs.getLong(KEY_SESSION_TIMESTAMP, 0)
        val elapsed = System.currentTimeMillis() - lastTimestamp
        return elapsed < SESSION_TIMEOUT_MS
    }

    /**
     * Clear the session (force re-authentication).
     */
    fun clearSession() {
        prefs.edit().remove(KEY_SESSION_TIMESTAMP).apply()
    }

    private fun generateSalt(): ByteArray {
        return ByteArray(SALT_LENGTH_BYTES).apply {
            SecureRandom().nextBytes(this)
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}

enum class AuthMethod {
    BIOMETRIC,
    PIN
}

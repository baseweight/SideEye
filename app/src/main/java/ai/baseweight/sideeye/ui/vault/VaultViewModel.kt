package ai.baseweight.sideeye.ui.vault

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.baseweight.sideeye.data.security.AuthMethod
import ai.baseweight.sideeye.data.security.BiometricAuthManager
import ai.baseweight.sideeye.data.security.VaultPinManager
import ai.baseweight.sideeye.data.vault.VaultImage
import ai.baseweight.sideeye.data.vault.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultUiState(
    val images: List<VaultImage> = emptyList(),
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val needsSetup: Boolean = false,
    val authMethod: AuthMethod = AuthMethod.BIOMETRIC,
    val selectedImages: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val authError: String? = null,
    val isBiometricAvailable: Boolean = false
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val vaultRepository = VaultRepository(application)
    private val pinManager = VaultPinManager(application)
    private val biometricManager = BiometricAuthManager(application)

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        checkInitialState()
    }

    private fun checkInitialState() {
        val isBiometricAvailable = biometricManager.isBiometricAvailable()
        val isPinSet = pinManager.isPinSet()
        val authMethod = pinManager.getAuthMethod()

        // Check if session is still valid
        val isAuthenticated = pinManager.isSessionValid()

        _uiState.update {
            it.copy(
                needsSetup = !isPinSet && !isBiometricAvailable,
                isAuthenticated = isAuthenticated,
                authMethod = authMethod,
                isBiometricAvailable = isBiometricAvailable,
                isLoading = false
            )
        }

        if (isAuthenticated) {
            loadVaultImages()
        }
    }

    fun needsAuthentication(): Boolean {
        val state = _uiState.value
        return !state.isAuthenticated
    }

    fun needsFirstTimeSetup(): Boolean {
        // First time setup is needed if no PIN is set
        return !pinManager.isPinSet()
    }

    fun setupPin(pin: String): Boolean {
        val success = pinManager.setPin(pin)
        if (success) {
            pinManager.setAuthMethod(AuthMethod.PIN)
            pinManager.updateSessionTimestamp()
            _uiState.update {
                it.copy(
                    isAuthenticated = true,
                    needsSetup = false,
                    authError = null,
                    authMethod = AuthMethod.PIN
                )
            }
            loadVaultImages()
        }
        return success
    }

    fun verifyPin(pin: String): Boolean {
        val success = pinManager.verifyPin(pin)
        if (success) {
            pinManager.updateSessionTimestamp()
            _uiState.update {
                it.copy(
                    isAuthenticated = true,
                    authError = null
                )
            }
            loadVaultImages()
        } else {
            _uiState.update {
                it.copy(authError = "Incorrect PIN. Please try again.")
            }
        }
        return success
    }

    fun authenticateWithBiometric(activity: FragmentActivity) {
        biometricManager.authenticateBiometricOnly(activity) { result ->
            when (result) {
                is BiometricAuthManager.AuthResult.Success -> {
                    pinManager.updateSessionTimestamp()
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            authError = null
                        )
                    }
                    loadVaultImages()
                }
                is BiometricAuthManager.AuthResult.Error -> {
                    _uiState.update {
                        it.copy(authError = result.errorMessage)
                    }
                }
                is BiometricAuthManager.AuthResult.Cancelled -> {
                    // User cancelled, no error to show
                }
                is BiometricAuthManager.AuthResult.Failed -> {
                    _uiState.update {
                        it.copy(authError = "Biometric authentication failed")
                    }
                }
            }
        }
    }

    fun authenticateWithDeviceCredential(activity: FragmentActivity) {
        biometricManager.authenticate(activity) { result ->
            when (result) {
                is BiometricAuthManager.AuthResult.Success -> {
                    pinManager.updateSessionTimestamp()
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            authError = null
                        )
                    }
                    loadVaultImages()
                }
                is BiometricAuthManager.AuthResult.Error -> {
                    _uiState.update {
                        it.copy(authError = result.errorMessage)
                    }
                }
                is BiometricAuthManager.AuthResult.Cancelled -> {
                    // User cancelled
                }
                is BiometricAuthManager.AuthResult.Failed -> {
                    _uiState.update {
                        it.copy(authError = "Authentication failed")
                    }
                }
            }
        }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authError = null) }
    }

    fun loadVaultImages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val images = vaultRepository.listVaultImages()
            _uiState.update {
                it.copy(
                    images = images,
                    isLoading = false
                )
            }
        }
    }

    fun toggleImageSelection(imageId: String) {
        _uiState.update { state ->
            val newSelection = if (imageId in state.selectedImages) {
                state.selectedImages - imageId
            } else {
                state.selectedImages + imageId
            }
            state.copy(
                selectedImages = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
    }

    fun selectAllImages() {
        _uiState.update { state ->
            state.copy(
                selectedImages = state.images.map { it.id }.toSet(),
                isSelectionMode = true
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedImages = emptySet(),
                isSelectionMode = false
            )
        }
    }

    fun deleteSelectedImages() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedImages.toList()
            selectedIds.forEach { id ->
                vaultRepository.removeFromVault(id)
            }
            clearSelection()
            loadVaultImages()
        }
    }

    fun restoreSelectedImages() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedImages.toList()
            selectedIds.forEach { id ->
                vaultRepository.restoreFromVault(id)
            }
            clearSelection()
            loadVaultImages()
        }
    }

    fun getDecryptedImageStream(imageId: String) = vaultRepository.getDecryptedStream(imageId)

    fun lockVault() {
        pinManager.clearSession()
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                images = emptyList(),
                selectedImages = emptySet(),
                isSelectionMode = false
            )
        }
    }

    fun updateSessionActivity() {
        if (_uiState.value.isAuthenticated) {
            pinManager.updateSessionTimestamp()
        }
    }

    // Settings-related methods
    fun getAuthMethod(): AuthMethod = pinManager.getAuthMethod()

    fun setAuthMethod(method: AuthMethod) {
        pinManager.setAuthMethod(method)
        _uiState.update { it.copy(authMethod = method) }
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        return pinManager.changePin(oldPin, newPin)
    }

    fun isAuthRequiredOnLaunch(): Boolean = pinManager.isAuthRequiredOnLaunch()

    fun setAuthRequiredOnLaunch(required: Boolean) {
        pinManager.setAuthRequiredOnLaunch(required)
    }

    fun isBiometricAvailable(): Boolean = biometricManager.isBiometricAvailable()
}

package ai.baseweight.sideeye.ui.onboarding

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.baseweight.sideeye.data.OnboardingManager
import ai.baseweight.sideeye.data.ai.FlagCategory
import ai.baseweight.sideeye.data.ai.ModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val isModelDownloading: Boolean = false,
    val modelDownloadProgress: Float = 0f,
    val modelDownloadError: String? = null,
    val isModelDownloaded: Boolean = false,
    val enabledCategories: Set<FlagCategory> = FlagCategory.entries
        .filter { it.defaultEnabled }
        .toSet()
)

enum class OnboardingStep {
    WELCOME,
    MODEL_DOWNLOAD,
    PERMISSIONS,
    VAULT_SETUP,
    CATEGORIES
}

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val onboardingManager = OnboardingManager(application)
    private val modelDownloader = ModelDownloader(application)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Check if model is already downloaded
        viewModelScope.launch {
            val isDownloaded = modelDownloader.isModelDownloaded(ModelDownloader.OMNINEURAL_MODEL_ID)
            _uiState.update { it.copy(isModelDownloaded = isDownloaded) }
        }
    }

    fun isOnboardingComplete(): Boolean = onboardingManager.isOnboardingComplete()

    fun goToNextStep() {
        _uiState.update { state ->
            val nextStep = when (state.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.MODEL_DOWNLOAD
                OnboardingStep.MODEL_DOWNLOAD -> OnboardingStep.PERMISSIONS
                OnboardingStep.PERMISSIONS -> OnboardingStep.VAULT_SETUP
                OnboardingStep.VAULT_SETUP -> OnboardingStep.CATEGORIES
                OnboardingStep.CATEGORIES -> OnboardingStep.CATEGORIES // Last step
            }
            state.copy(currentStep = nextStep)
        }
    }

    fun goToPreviousStep() {
        _uiState.update { state ->
            val prevStep = when (state.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.WELCOME // First step
                OnboardingStep.MODEL_DOWNLOAD -> OnboardingStep.WELCOME
                OnboardingStep.PERMISSIONS -> OnboardingStep.MODEL_DOWNLOAD
                OnboardingStep.VAULT_SETUP -> OnboardingStep.PERMISSIONS
                OnboardingStep.CATEGORIES -> OnboardingStep.VAULT_SETUP
            }
            state.copy(currentStep = prevStep)
        }
    }

    fun downloadModel() {
        if (_uiState.value.isModelDownloading) return

        viewModelScope.launch {
            _uiState.update { it.copy(
                isModelDownloading = true,
                modelDownloadProgress = 0f,
                modelDownloadError = null
            ) }

            modelDownloader.downloadModel(ModelDownloader.OMNINEURAL_MODEL_ID).collect { state ->
                when (state) {
                    is ModelDownloader.DownloadState.Idle,
                    is ModelDownloader.DownloadState.ListingFiles -> {
                        // Still preparing
                    }
                    is ModelDownloader.DownloadState.Downloading -> {
                        val progress = if (state.totalBytes > 0) {
                            state.bytesDownloaded.toFloat() / state.totalBytes
                        } else 0f
                        _uiState.update { it.copy(modelDownloadProgress = progress) }
                    }
                    is ModelDownloader.DownloadState.Completed -> {
                        onboardingManager.setModelDownloaded(true)
                        _uiState.update { it.copy(
                            isModelDownloading = false,
                            isModelDownloaded = true,
                            modelDownloadProgress = 1f
                        ) }
                    }
                    is ModelDownloader.DownloadState.Error -> {
                        _uiState.update { it.copy(
                            isModelDownloading = false,
                            modelDownloadError = state.message
                        ) }
                    }
                }
            }
        }
    }

    fun markPermissionsGranted() {
        onboardingManager.setPermissionsGranted(true)
    }

    fun markVaultSetup() {
        onboardingManager.setVaultSetup(true)
    }

    fun toggleCategory(category: FlagCategory) {
        _uiState.update { state ->
            val newCategories = if (category in state.enabledCategories) {
                state.enabledCategories - category
            } else {
                state.enabledCategories + category
            }
            state.copy(enabledCategories = newCategories)
        }
    }

    fun completeOnboarding() {
        onboardingManager.setCategoriesConfigured(true)
        onboardingManager.setOnboardingComplete(true)
    }

    fun isOnWifi(): Boolean {
        val connectivityManager = getApplication<Application>()
            .getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}

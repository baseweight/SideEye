# SideEye

Smart photo management with privacy at its core. SideEye uses on-device AI to scan your gallery for sensitive content and helps you delete, keep, or securely vault flagged images — all without your photos ever leaving your device.

Built with [Nexa SDK](https://sdk.nexa.ai/) and the OmniNeural-4B Vision Language Model, optimized for Qualcomm NPU inference.

## Features

### Smart Scan
On-device content moderation powered by OmniNeural-4B VLM running through Nexa SDK. Scans gallery photos and classifies them into configurable categories:
- **Nudity** — visible explicit content
- **Suggestive** — bikinis, swimwear, revealing clothing
- **Drugs** — cannabis, drug paraphernalia
- **Embarrassing** — drunk moments, unflattering photos

Flagged images are presented in a swipeable card interface for quick triage: swipe to delete, keep, or vault.

### Private Vault
Encrypted storage for sensitive images using AES-256-GCM. Protected by PIN (hashed with PBKDF2, 100k iterations) and optional biometric authentication with a 15-minute session timeout.

### Gallery
Full photo gallery with selection mode, batch operations, and a Google Photos cloud backup warning.

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material Design 3)
- **Nexa SDK 0.0.20** with NPU plugin for on-device VLM inference
- **OmniNeural-4B** Vision Language Model (~2.4GB, downloaded during onboarding)
- **AndroidX Security Crypto** for AES-256-GCM encrypted vault storage
- **AndroidX Biometric** for fingerprint/face authentication
- **Coil** for image loading
- **MVVM** architecture with ViewModels + StateFlow
- **Navigation Compose** for single-activity navigation

## Requirements

- Android 9+ (API 28)
- ~2.4GB storage for the AI model
- Qualcomm device with NPU recommended for optimal inference performance

## Building

```bash
./gradlew assembleDebug
```

Install to a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/java/ai/baseweight/sideeye/
├── MainActivity.kt              # Entry point and navigation graph
├── data/
│   ├── ai/
│   │   ├── ImageAnalyzer.kt     # VLM inference via Nexa SDK
│   │   ├── ModelDownloader.kt   # Model download with S3/HuggingFace fallback
│   │   └── FlagCategory.kt     # Content classification categories
│   ├── security/
│   │   ├── SecurityManager.kt   # Encrypted preferences
│   │   ├── VaultPinManager.kt   # PBKDF2 PIN hashing
│   │   └── BiometricAuthManager.kt
│   └── vault/
│       └── VaultRepository.kt   # Encrypted file storage
└── ui/
    ├── gallery/                 # Photo grid and image viewer
    ├── moderation/              # Smart scan settings and swipe UI
    ├── vault/                   # Vault auth, browser, and settings
    ├── onboarding/              # Setup flow (model download, permissions, PIN)
    ├── about/                   # App info and legal links
    ├── splash/                  # Launch screen
    └── theme/                   # Material3 theming
```

## How It Works

1. **Onboarding** — Downloads the OmniNeural-4B model (WiFi-aware), requests permissions, and optionally sets up the vault PIN
2. **Smart Scan** — Queries MediaStore for new photos, resizes each to 448x448, runs inference through Nexa SDK on the NPU, and parses the VLM response for content classification
3. **Triage** — Flagged images appear as swipeable cards: delete, keep, or move to the encrypted vault
4. **Vault** — Encrypts images with AES-256-GCM, stores them in the app's private directory, and removes the originals from the gallery via MediaStore API

## License

Copyright 2024 Baseweight. All rights reserved.

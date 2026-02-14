# SideEye

Smart photo management with privacy at its core. SideEye uses on-device AI to scan your gallery for sensitive content and helps you delete, keep, or securely vault flagged images — all without your photos ever leaving your device.

## What It Does

SideEye is an Android app that combines on-device AI content moderation with encrypted photo storage:

- **Smart Scan** — Scans your photo gallery using the OmniNeural-4B Vision Language Model running entirely on-device. Classifies images into configurable categories (nudity, suggestive, drugs, embarrassing) and presents flagged images in a swipeable triage interface where you can delete, keep, or vault each one.
- **Private Vault** — AES-256-GCM encrypted storage for sensitive images, protected by PIN (PBKDF2 hashed, 100k iterations) and optional biometric authentication with a 15-minute session timeout.
- **Gallery** — Full photo gallery with multi-select, batch vault operations, and Google Photos cloud backup detection that warns you before sensitive images sync.
- **Complete Privacy** — All AI inference happens on-device via Qualcomm NPU. No images, metadata, or analysis results are ever sent to a server.

## How to Build and Run

### Prerequisites

- Android Studio (or just the Android SDK with command-line tools)
- JDK 8+
- An Android device running Android 9+ (API 28)
- A Qualcomm device with NPU is required for Smart Scan (e.g. Snapdragon 8 Gen 3)

### Build

```bash
git clone https://github.com/baseweight/SideEye.git
cd SideEye
./gradlew assembleDebug
```

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### First Run

1. Launch SideEye — the onboarding flow will guide you through:
   - Downloading the OmniNeural-4B model (~2.4GB, WiFi recommended)
   - Granting storage permissions
   - Optionally setting up a vault PIN
   - Choosing which content categories to scan for
2. The app opens to your photo gallery
3. Tap the overflow menu and select **Smart Scan** to analyze your photos
4. Flagged images appear as swipeable cards — swipe to delete, keep, or vault

## Where and Why Nexa SDK Is Used

Nexa SDK (`ai.nexa:core:0.0.20`) is the AI inference engine that powers SideEye's Smart Scan feature. It provides the runtime for executing the OmniNeural-4B Vision Language Model on Qualcomm's Neural Processing Unit (NPU).

### Where

- **`ImageAnalyzer.kt`** (`app/src/main/java/ai/baseweight/sideeye/data/ai/ImageAnalyzer.kt`) — The core integration point. Uses `NexaSdk.getInstance().init()` to initialize the SDK, then builds a `VlmWrapper` with `plugin_id = "npu"` to run inference on the NPU. Each image is resized to 448x448, passed to the VLM with a classification prompt, and the streamed response is parsed for content categories.

- **`ModelDownloader.kt`** (`app/src/main/java/ai/baseweight/sideeye/data/ai/ModelDownloader.kt`) — Downloads the OmniNeural-4B model files from Nexa's S3 model hub (`nexa-model-hub-bucket.s3.us-west-1.amazonaws.com`) with a HuggingFace fallback (`NexaAI/OmniNeural-4B-mobile`).

### Why

SideEye needs to run a 4-billion parameter Vision Language Model entirely on-device to maintain its privacy guarantee — photos never leave the phone. Nexa SDK was chosen because:

1. **NPU acceleration** — The `npu` plugin leverages Qualcomm's dedicated AI hardware for fast, battery-efficient inference instead of burning through CPU/GPU
2. **VLM support** — Nexa SDK provides first-class support for Vision Language Models through `VlmWrapper`, handling the multimodal input pipeline (image + text prompt) and streaming token generation
3. **Model ecosystem** — The OmniNeural-4B model is available through Nexa's model hub, pre-optimized for mobile NPU execution

### Inference Flow

```
Gallery Image → Resize to 448x448 → NexaSdk VlmWrapper (NPU plugin)
    → OmniNeural-4B classifies content → Parse "Category: N" response
    → Flag image if category matches user settings
```

The model is reinitialized every 4 inferences to maintain NPU stability, and all inference runs on `Dispatchers.IO` to keep the UI responsive.

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

## License

Copyright 2026 Baseweight. All rights reserved.

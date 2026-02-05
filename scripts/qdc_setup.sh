#!/bin/bash
# QDC Device Setup Script for SideEye VLM Benchmark
# Handles: APK install, model download, test images, and permissions

set -e

# Must use ADB_SERVER_SOCKET for QDC
export ADB_SERVER_SOCKET=tcp:localhost:5037

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TEST_IMAGES_DIR="$HOME/test_photo_set"

echo "=== QDC Device Setup for SideEye ==="
echo "Project: $PROJECT_DIR"
echo "Test images: $TEST_IMAGES_DIR"
echo ""

# Check ADB connection
echo "Checking ADB connection..."
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No device connected. Make sure SSH tunnel is running:"
    echo "  ssh -i ~/.ssh/qdc_id_2026-1-29_06.pem -L 5037:<device>.sa.svc.cluster.local:5037 -N sshtunnel@ssh.qdc.qualcomm.com"
    exit 1
fi
DEVICE=$(adb devices | grep "device$" | awk '{print $1}')
echo "Connected to device: $DEVICE"
echo ""

# Build APKs
echo "Building APKs..."
cd "$PROJECT_DIR"
./gradlew assembleDebug assembleDebugAndroidTest 2>&1 | tail -5
echo ""

# Install APKs
echo "Installing APKs..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
echo ""

# Grant permissions
echo "Granting storage permissions..."
adb shell pm grant ai.baseweight.sideeye android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb shell pm grant ai.baseweight.sideeye android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb shell appops set ai.baseweight.sideeye MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true
echo ""

# Check if model is downloaded
echo "Checking model status..."
MODEL_CHECK=$(adb shell "ls /data/data/ai.baseweight.sideeye/files/models/omnineural-4b-mobile/files-1-1.nexa 2>/dev/null && echo 'exists'" || echo "")
if [ "$MODEL_CHECK" != "exists" ]; then
    echo "Model not found. Downloading OmniNeural (this takes ~5 minutes)..."
    adb shell am instrument -w -e class ai.baseweight.sideeye.vlm.ModelDownloadTest#downloadOmniNeural ai.baseweight.sideeye.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tail -20
else
    echo "Model already downloaded."
fi
echo ""

# Push test images
if [ -d "$TEST_IMAGES_DIR" ]; then
    echo "Pushing test images..."
    # Remove existing directory first to avoid ownership issues
    adb shell "rm -rf /sdcard/Android/data/ai.baseweight.sideeye/files/test_photo_set" 2>/dev/null || true
    adb push "$TEST_IMAGES_DIR" /sdcard/Android/data/ai.baseweight.sideeye/files/test_photo_set

    # Fix ownership - this is the key step!
    echo "Fixing file ownership..."
    adb shell "chown -R u0_a230:ext_data_rw /sdcard/Android/data/ai.baseweight.sideeye/files/test_photo_set/"

    # Verify
    FILE_COUNT=$(adb shell "ls /sdcard/Android/data/ai.baseweight.sideeye/files/test_photo_set/*.json 2>/dev/null | wc -l")
    echo "Test images pushed. JSON files found: $FILE_COUNT"
else
    echo "WARNING: Test images not found at $TEST_IMAGES_DIR"
    echo "Skipping test image push."
fi
echo ""

echo "=== Setup Complete ==="
echo ""
echo "Run benchmark with:"
echo "  ADB_SERVER_SOCKET=tcp:localhost:5037 adb shell am instrument -w -e class ai.baseweight.sideeye.vlm.OmniNeuralBenchmark#describeImagesTest ai.baseweight.sideeye.test/androidx.test.runner.AndroidJUnitRunner"
echo ""
echo "Or full benchmark:"
echo "  ADB_SERVER_SOCKET=tcp:localhost:5037 adb shell am instrument -w -e class ai.baseweight.sideeye.vlm.OmniNeuralBenchmark#runFullBenchmark ai.baseweight.sideeye.test/androidx.test.runner.AndroidJUnitRunner"

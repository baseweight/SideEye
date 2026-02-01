#!/bin/bash
# Qualcomm Device Cloud - VLM Evaluation Test Runner
# Upload this script along with app-debug.apk and app-debug-androidTest.apk

set -e

APP_APK="app-debug.apk"
TEST_APK="app-debug-androidTest.apk"
PACKAGE="ai.baseweight.sideeye"
TEST_PACKAGE="${PACKAGE}.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
RESULTS_DIR="/sdcard/Android/data/${PACKAGE}/files/vlm_eval_results"

echo "========================================"
echo "SideEye VLM Evaluation - QDC Runner"
echo "========================================"

# Check if APKs exist
if [ ! -f "$APP_APK" ]; then
    echo "ERROR: $APP_APK not found"
    exit 1
fi

if [ ! -f "$TEST_APK" ]; then
    echo "ERROR: $TEST_APK not found"
    exit 1
fi

# Wait for device
echo "[1/8] Waiting for device..."
adb wait-for-device

# Get device info
echo "[2/8] Device info:"
adb shell getprop ro.product.model
adb shell getprop ro.board.platform
adb shell getprop ro.build.version.release

# Uninstall previous versions (ignore errors if not installed)
echo "[3/8] Cleaning up previous installation..."
adb uninstall $PACKAGE 2>/dev/null || true
adb uninstall $TEST_PACKAGE 2>/dev/null || true

# Install APKs
echo "[4/8] Installing app APK..."
adb install -r -g "$APP_APK"

echo "[5/8] Installing test APK..."
adb install -r -g "$TEST_APK"

# Create results directory
adb shell mkdir -p "$RESULTS_DIR"

# Step 1: Download the model (if not already present)
echo "[6/8] Downloading OmniNeural model (this may take a while)..."
adb shell am instrument -w -r \
    -e class ai.baseweight.sideeye.vlm.ModelDownloadTest#downloadOmniNeural \
    $TEST_PACKAGE/$RUNNER

DOWNLOAD_STATUS=$?
if [ $DOWNLOAD_STATUS -ne 0 ]; then
    echo "WARNING: Model download test returned non-zero status: $DOWNLOAD_STATUS"
fi

# Step 2: Run the VLM evaluation
echo "[7/8] Running VLM evaluation tests..."

# Choose one of these test options:

# Option A: Full benchmark (recommended)
adb shell am instrument -w -r \
    -e class ai.baseweight.sideeye.vlm.VlmEvalTest#benchmarkOmniNeural \
    $TEST_PACKAGE/$RUNNER

# Option B: Quick sanity test (2 images)
# adb shell am instrument -w -r \
#     -e class ai.baseweight.sideeye.vlm.VlmEvalTest#quickSanityTest \
#     $TEST_PACKAGE/$RUNNER

# Option C: Initialization test only (fastest)
# adb shell am instrument -w -r \
#     -e class ai.baseweight.sideeye.vlm.VlmEvalTest#testModelInitialization \
#     $TEST_PACKAGE/$RUNNER

# Capture test status
TEST_STATUS=$?

# Pull results
echo "[8/8] Pulling results..."
mkdir -p ./results
adb pull "$RESULTS_DIR/" ./results/ 2>/dev/null || echo "No results files to pull"

# Also capture logcat for debugging
adb logcat -d -s "VlmEvalTest:*" "OmniNeuralAdapter:*" "VlmBenchmarkRunner:*" "ModelDownloader:*" "ModelDownloadTest:*" > ./results/logcat.txt 2>/dev/null || true

echo "========================================"
echo "Test completed with status: $TEST_STATUS"
echo "Results saved to ./results/"
echo "========================================"

# List result files
ls -la ./results/ 2>/dev/null || true

exit $TEST_STATUS

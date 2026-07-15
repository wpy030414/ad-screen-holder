#!/bin/bash
# 完整构建+签名的单命令脚本
# 用法: ./build-and-sign.sh

set -e
export ANDROID_SDK_ROOT="/opt/homebrew/share/android-commandlinetools"
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@25/25.0.3/libexec/openjdk.jdk/Contents/Home"

cd "$(dirname "$0")"
PROJECT_DIR="$(pwd)"

echo "Building..."
gradle clean assembleRelease 2>&1 | tail -3

APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
APK_ALIGNED="app/build/outputs/apk/release/app-aligned.apk"
APK_SIGNED="app/build/outputs/apk/release/AdScreenHolder-release.apk"
KEYSTORE="$PROJECT_DIR/release.keystore"
BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/34.0.0"

echo "Aligning..."
"$BUILD_TOOLS/zipalign" -p -f 4 "$APK_UNSIGNED" "$APK_ALIGNED" 2>/dev/null

echo "Signing..."
"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:adscreen123 \
    --ks-key-alias adscreen \
    --key-pass pass:adscreen123 \
    --out "$APK_SIGNED" \
    "$APK_ALIGNED" 2>/dev/null

echo ""
echo "Done!"
ls -lh "$APK_SIGNED"

cp "$APK_SIGNED" "$PROJECT_DIR/AdScreenHolder-release.apk"
echo "Output: $PROJECT_DIR/AdScreenHolder-release.apk"

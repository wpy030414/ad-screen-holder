#!/bin/bash
# 完整构建+签名的单命令脚本
# 用法: ./build-and-sign.sh
# 输出: AdScreenHolder-<versionName>.apk（带版本号）

set -e
export ANDROID_SDK_ROOT="/opt/homebrew/share/android-commandlinetools"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@25/25.0.3/libexec/openjdk.jdk/Contents/Home"

cd "$(dirname "$0")"
PROJECT_DIR="$(pwd)"

# 从 app/build.gradle 读取版本号
VERSION_NAME=$(grep -E 'versionName\s+' app/build.gradle | head -1 | sed -E 's/^[^"]*"([^"]*)".*$/\1/')
VERSION_CODE=$(grep -E 'versionCode\s+' app/build.gradle | head -1 | sed -E 's/^[^0-9]*([0-9]+).*$/\1/')
TAG="AVD Manager ${VERSION_NAME}"

echo "=== Building ${TAG} (versionCode=${VERSION_CODE}) ==="

echo "Building..."
gradle clean assembleRelease 2>&1 | tail -3

APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
APK_ALIGNED="app/build/outputs/apk/release/app-aligned.apk"
APK_SIGNED="app/build/outputs/apk/release/AdScreenHolder-${VERSION_NAME}.apk"
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

# 同时拷贝一份到项目根目录，文件名带版本号
cp "$APK_SIGNED" "$PROJECT_DIR/AdScreenHolder-${VERSION_NAME}.apk"
echo "Output: $PROJECT_DIR/AdScreenHolder-${VERSION_NAME}.apk"

# 输出版本信息供后续步骤（如 CI）使用
echo "VERSION_NAME=$VERSION_NAME" > "$PROJECT_DIR/.version"
echo "VERSION_CODE=$VERSION_CODE" >> "$PROJECT_DIR/.version"
echo "TAG=$TAG" >> "$PROJECT_DIR/.version"

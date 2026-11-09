#!/usr/bin/env bash
# 编译 APK，按发布规则命名后收进 dist/。
#
# 版本号由 Gradle 按 git 提交数自动生成（见 app/build.gradle.kts），这里只管两件事：
#   1. 打 tag 时把 tag 名传给 -PappVersionName，让包内版本和 Release 名对得上
#   2. 有完整签名材料就出 release 包，缺就退回 debug 包
set -euo pipefail

cd "$(dirname "$0")/.."

# 构建机上 ANDROID_SDK_ROOT 由 .ci/android.Dockerfile 提供；
# local.properties 已被 .gitignore 挡住，只在这里现写，不进提交。
if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
  echo "sdk.dir=${ANDROID_SDK_ROOT}" > local.properties
fi

# ---- 签名材料 ----
# 正常路径：密钥仓库 imports 注入 4 个环境变量，其中 keystore 是 base64。
# 本地调试：在 .ci/signing.properties 里写 keystoreFile 等四项（该文件已 gitignore）。
SIGNING_READY=false
if [ -n "${ANDROID_KEYSTORE_BASE64:-}" ]; then
  mkdir -p build-secrets
  printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 -d > build-secrets/release.jks
  export ANDROID_KEYSTORE_FILE="$PWD/build-secrets/release.jks"
  chmod 600 "$ANDROID_KEYSTORE_FILE"
fi

if [ -n "${ANDROID_KEYSTORE_FILE:-}" ] && [ -f "$ANDROID_KEYSTORE_FILE" ] \
   && [ -n "${ANDROID_KEYSTORE_PASSWORD:-}" ] && [ -n "${ANDROID_KEY_ALIAS:-}" ] \
   && [ -n "${ANDROID_KEY_PASSWORD:-}" ]; then
  SIGNING_READY=true
  echo "签名：release"
else
  echo "签名：debug（没拿到签名材料，包能装但上不了架，见 docs/签名与版本号.md）"
fi

# 版本号：默认跟随提交数；tag 构建用 tag 名作对外版本号
GRADLE_ARGS=(--no-daemon --stacktrace)
if [ "${CNB_IS_TAG:-false}" = "true" ] && [ -n "${CNB_BRANCH:-}" ]; then
  GRADLE_ARGS+=("-PappVersionName=${CNB_BRANCH#v}")
fi

if [ "$SIGNING_READY" = "true" ]; then
  ./gradlew "${GRADLE_ARGS[@]}" assembleRelease
  APK="app/build/outputs/apk/release/app-release.apk"
  VARIANT="release"
else
  ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
  APK="app/build/outputs/apk/debug/app-debug.apk"
  VARIANT="debug"
fi

[ -f "$APK" ] || { echo "没有找到 $APK"; exit 1; }

# 必须带上和编译相同的 -P 参数，否则打印的是默认版本号，和包里的对不上
./gradlew -q "${GRADLE_ARGS[@]}" printVersion | sed 's/^/版本：/'

if [ "${CNB_IS_TAG:-false}" = "true" ]; then
  APK_NAME="html-manager-${CNB_BRANCH}-${VARIANT}.apk"
else
  APK_NAME="html-manager-nightly-${CNB_COMMIT_SHORT}-${VARIANT}.apk"
fi

mkdir -p dist
cp "$APK" "dist/${APK_NAME}"

# 签名校验：签过就打出证书指纹，方便和手机上的安装来源对账
if [ "$SIGNING_READY" = "true" ]; then
  "${ANDROID_SDK_ROOT:-/opt/android-sdk}"/build-tools/34.0.0/apksigner verify --print-certs \
    "dist/${APK_NAME}" | head -5 || true
fi

echo "产物：dist/${APK_NAME}"
sha256sum "dist/${APK_NAME}"

#!/usr/bin/env bash
# 生成一份 Android 签名密钥，并打印可直接粘进密钥仓库的 YAML。
# 用法：bash .ci/setup-signing.sh [别名] [输出路径]
set -euo pipefail

ALIAS="${1:-htmlmanager}"
OUT="${2:-$(cd "$(dirname "$0")/.." && pwd)/build-secrets/release.jks}"

command -v keytool >/dev/null || { echo "找不到 keytool，先装 JDK"; exit 1; }

if [ -f "$OUT" ]; then
  echo "已存在：$OUT"
  echo "覆盖会换掉签名身份，已安装的包将无法升级。要重新生成请先手动删掉它。"
  exit 1
fi

mkdir -p "$(dirname "$OUT")"
STORE_PASS=$(head -c 18 /dev/urandom | base64 | tr -d '/+=' | head -c 24)
KEY_PASS="$STORE_PASS"

keytool -genkeypair -v -keystore "$OUT" -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -dname "CN=HTML Manager, O=djzrs, C=CN" >/dev/null

if command -v base64 >/dev/null; then
  B64=$(base64 -w0 "$OUT" 2>/dev/null || base64 "$OUT" | tr -d '\n')
else
  B64=$(openssl base64 -A -in "$OUT")
fi

cat <<YAML

密钥文件：$OUT
这份文件不要提交，也不要丢——丢了就再也签不出同一个身份。

把下面整段粘进密钥仓库（如 djzrs/android-signing 的 html-manager-signing.yml）：

ANDROID_KEYSTORE_BASE64: "$B64"
ANDROID_KEYSTORE_PASSWORD: "$STORE_PASS"
ANDROID_KEY_ALIAS: "$ALIAS"
ANDROID_KEY_PASSWORD: "$KEY_PASS"

allow_slugs:
  - djzrs/AndriodCloudBuild
allow_events:
  - push
  - tag_push

然后改 .cnb.yml 里 html-manager-build 的 imports 指向该文件。
本地要用，写 .ci/signing.properties：

keystoreFile=$OUT
keystorePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$KEY_PASS

YAML

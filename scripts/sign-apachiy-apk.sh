#!/usr/bin/env bash
# =============================================================================
# sign-apachiy-apk.sh — create a development keystore for Apachiy TV
# Usage:  sign-apachiy-apk.sh [output-dir]
#
# This creates a self-signed RSA-2048 keystore valid for 25 years. It is meant
# for local dev builds. For production, use a properly secured keystore stored
# outside the repo (see docs/PRODUCTION_DEPLOYMENT.md).
# =============================================================================
set -euo pipefail

OUT_DIR="${1:-keystore}"
mkdir -p "$OUT_DIR"

KEYSTORE="$OUT_DIR/apachiy.jks"
ALIAS="apachiy"
VALIDITY=9125  # ~25 years
PASSWORD="apachiy-dev"

if [ -f "$KEYSTORE" ]; then
  echo "[sign-apachiy-apk] $KEYSTORE already exists. Skipping."
  exit 0
fi

echo "[sign-apachiy-apk] generating RSA-2048 keystore at $KEYSTORE"
keytool -genkey -noprompt \
  -alias "$ALIAS" \
  -dname "CN=Apachiy TV (dev), OU=Apachiy, O=Apachiy, L=Local, S=Local, C=AR" \
  -keystore "$KEYSTORE" \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY" 2>&1 | sed 's/^/  /'

cat > "$OUT_DIR/release.keystore.properties" <<EOF
APACHIY_RELEASE_STORE_FILE=$KEYSTORE
APACHIY_RELEASE_KEY_ALIAS=$ALIAS
APACHIY_RELEASE_KEY_PASSWORD=$PASSWORD
APACHIY_RELEASE_STORE_PASSWORD=$PASSWORD
EOF
chmod 600 "$OUT_DIR/release.keystore.properties"

echo
echo "Keystore ready. To use it with the Apachiy build:"
echo "  cp $OUT_DIR/release.keystore.properties $OUT_DIR/../release.keystore.properties"
echo "  ./gradlew :app:assembleFullRelease"
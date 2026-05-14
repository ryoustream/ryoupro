#!/bin/bash
# ============================================================
# generate_keystore.sh
# Generates a keystore for Ryou Player signing
# Run this ONCE locally, then upload to GitHub Secrets
# ============================================================

set -e

KEYSTORE_FILE="keystore.jks"
STORE_PASSWORD="defaultryoustream"
KEY_ALIAS="ryoustream"
KEY_PASSWORD="defaultryoustream"
VALIDITY_DAYS=10000

echo "🔑 Generating Ryou Player keystore..."

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY_DAYS" \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "CN=Ryou Player, OU=RyouStream, O=RyouStream, L=Unknown, S=Unknown, C=ID"

echo ""
echo "✅ Keystore generated: $KEYSTORE_FILE"
echo ""
echo "📋 Add to GitHub Secrets:"
echo "---"
echo "KEYSTORE_BASE64 = $(base64 -w 0 $KEYSTORE_FILE)"
echo "KEYSTORE_PASSWORD = $STORE_PASSWORD"
echo "KEY_ALIAS = $KEY_ALIAS"
echo "KEY_PASSWORD = $KEY_PASSWORD"
echo "---"
echo ""
echo "⚠️  Keep keystore.jks safe — it is in .gitignore"

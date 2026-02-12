#!/bin/bash

# Load credentials
source "$(dirname "$0")/apple_creds.sh"

# --- CONFIGURATION ---
SHOULD_NOTARIZE=true  
BASE_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET_DIR="$BASE_DIR/target"
ENTITLEMENTS="$(dirname "$0")/entitlements.plist"

APP_PATH=$(ls -d "$TARGET_DIR"/*.app | head -n 1)

if [ -z "$APP_PATH" ]; then
    echo "Error: No .app file found in $TARGET_DIR"
    exit 1
fi

APP_NAME=$(basename "$APP_PATH")
NAME_ONLY="${APP_NAME%.*}"
DMG_PATH="$TARGET_DIR/${NAME_ONLY}-signed.dmg"
BUNDLE_DIR="$TARGET_DIR/bundle"

echo "--- Starting Signing Process for: $APP_NAME ---"

# 1. DISCOVER AND CLEAN JARs (Handles hidden native code)
echo "--- Scanning JARs for native libraries & stripping i386 ---"
find "$APP_PATH/Contents/Java" -name "*.jar" | while read -r JAR_PATH; do
    if unzip -l "$JAR_PATH" | grep -qE "\.dylib|\.jnilib|\.so"; then
        echo "Processing JAR: $(basename "$JAR_PATH")"
        TMP_DIR=$(mktemp -d)
        unzip -q "$JAR_PATH" -d "$TMP_DIR"
        
        find "$TMP_DIR" -type f \( -name "*.dylib" -or -name "*.jnilib" -or -name "*.so" \) | while read -r LIB; do
            # Strip 32-bit architecture (Apple rejection trigger)
            if lipo -info "$LIB" | grep -q "i386"; then
                echo "  Stripping i386 from: $(basename "$LIB")"
                lipo "$LIB" -remove i386 -output "$LIB" 2>/dev/null || echo "  Note: i386 removal skipped"
            fi

            # Sign individual binary with timestamp
            codesign --force --options runtime --timestamp --sign "$CERT_NAME" "$LIB"
        done
        
        # Update original JAR with signed/cleaned binaries
        (cd "$TMP_DIR" && zip -u -rq "$JAR_PATH" .)
        rm -rf "$TMP_DIR"
    fi
done

# 2. SIGN THE APP BUNDLE (DEEP SIGN)
# We use --deep here to catch the JRE and all sub-folders in one go.
# This replaces the separate JRE find command for a cleaner execution.
echo "--- Performing Deep Sign on the .app bundle ---"
xattr -cr "$APP_PATH"
codesign --force --options runtime --deep --timestamp --sign "$CERT_NAME" --entitlements "$ENTITLEMENTS" "$APP_PATH"

# 3. CREATE DMG
echo "--- Building DMG ---"
mkdir -p "$BUNDLE_DIR"
cp -r "$APP_PATH" "$BUNDLE_DIR/"
ln -s /Applications "$BUNDLE_DIR/Applications"
rm -f "$DMG_PATH"
hdiutil create -srcfolder "$BUNDLE_DIR" -volname "PAMGuard Installer" -fs HFS+ -o "$DMG_PATH"
rm -rf "$BUNDLE_DIR"

# 4. SIGN DMG
echo "--- Signing the final DMG ---"
codesign --force --timestamp --sign "$CERT_NAME" "$DMG_PATH"

# 5. NOTARIZATION
if [ "$SHOULD_NOTARIZE" = true ]; then
   echo "--- Submitting to Apple (This may take several minutes) ---"
   xcrun notarytool submit "$DMG_PATH" --apple-id "$APPLE_ID" --password "$APPLE_PASSWORD" --team-id "$TEAM_ID" --wait

   echo "--- Stapling (with CloudKit retry loop) ---"
   for i in {1..6}; do
       if xcrun stapler staple "$DMG_PATH"; then
           echo "Success: Ticket stapled."
           break
       else
           echo "Waiting for CloudKit propagation... ($i/6)"
           sleep 30
       fi
   done
fi

# 6. VERIFICATION
echo "--- Final Gatekeeper Check ---"
spctl --assess --type install --verbose "$DMG_PATH"

echo "Done! Generated: $DMG_PATH"
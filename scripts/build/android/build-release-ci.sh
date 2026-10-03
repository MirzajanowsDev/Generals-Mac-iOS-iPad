#!/usr/bin/env bash
# GeneralsX @build Codex 04/10/2026 Linux CI build with strict native-library and APK verification.
set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p logs release
exec > >(tee logs/android-ci-release.log) 2>&1
SDK_DIR="${ANDROID_HOME:?ANDROID_HOME must point to the installed Android SDK}"
NDK_DIR="$SDK_DIR/ndk/27.1.12297006"
BUILD_DIR="$PWD/build/android-game"
CMAKE="$SDK_DIR/cmake/3.30.5/bin/cmake"
TOOLS="$SDK_DIR/build-tools/35.0.0"
export ANDROID_NDK_HOME="$NDK_DIR"
"$CMAKE" -S . -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
  -DCMAKE_BUILD_TYPE=Release -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 \
  -DANDROID_STL=c++_shared -DSAGE_USE_DX8=OFF -DSAGE_USE_SDL3=ON \
  -DSAGE_USE_OPENAL=ON -DSAGE_USE_GLM=ON -DSAGE_DXVK_USE_LOCAL_FORK=ON \
  -DRTS_BUILD_GENERALS=OFF -DRTS_BUILD_ZEROHOUR=ON -DRTS_BUILD_CORE_TOOLS=OFF \
  -DRTS_BUILD_ZEROHOUR_TOOLS=OFF -DRTS_BUILD_ZEROHOUR_EXTRAS=OFF \
  -DRTS_BUILD_OPTION_FFMPEG=OFF -DRTS_BUILD_OPTION_DEBUG=OFF \
  -DSAGE_UPDATE_CHECK=OFF -DRTS_CRASHDUMP_ENABLE=OFF -DRTS_BUILD_OPTION_SAGE_PATCH=OFF
"$CMAKE" --build "$BUILD_DIR" --target z_generals dxvk_d3d8_install --parallel 2

JNI_DIR="$PWD/android/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$JNI_DIR"
RUNTIME_LIBS=(libmain.so libdxvk_d3d8.so libdxvk_d3d9.so libSDL3.so libSDL3_image.so libopenal.so libfreetype.so libglm.so libgamespy.so)
for lib in "${RUNTIME_LIBS[@]}"; do
  source="$(find "$BUILD_DIR" \( -type f -o -type l \) -name "$lib" -print -quit)"
  if [[ -z "$source" || ! -s "$source" ]]; then
    echo "ERROR: required native library not built: $lib"
    exit 1
  fi
  cp -L "$source" "$JNI_DIR/$lib"
done
cp "$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$JNI_DIR/"
"$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-debug "$JNI_DIR/libmain.so"
test -s "$BUILD_DIR/_deps/sdl3-src/android-project/app/src/main/java/org/libsdl/app/SDLActivity.java"
for font in arial arialbold couriernew timesnewroman; do
  test -s "android/app/src/main/assets/fonts/$font.ttf"
done
printf 'sdk.dir=%s\n' "$SDK_DIR" > android/local.properties
gradle --no-daemon -p android -PSAGE_SKIP_NATIVE_BUILD=true assembleRelease
UNSIGNED="android/app/build/outputs/apk/release/app-release-unsigned.apk"
test -s "$UNSIGNED"
"$TOOLS/zipalign" -p -f 4 "$UNSIGNED" release/aligned.apk
# CI signing is for sideloading; production signing credentials are not required.
keytool -genkeypair -noprompt -keystore "$RUNNER_TEMP/generals-lan.keystore" \
  -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
  -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
"$TOOLS/apksigner" sign --ks "$RUNNER_TEMP/generals-lan.keystore" \
  --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
  --out release/GeneralsZH-LAN-release.apk release/aligned.apk
"$TOOLS/apksigner" verify --verbose release/GeneralsZH-LAN-release.apk | tee release/apk-verification.txt
"$TOOLS/zipalign" -c -p 4 release/GeneralsZH-LAN-release.apk
python3 - <<'PY'
import zipfile
from pathlib import Path
apk = Path('release/GeneralsZH-LAN-release.apk')
required = ['main', 'dxvk_d3d8', 'dxvk_d3d9', 'SDL3', 'SDL3_image', 'openal', 'freetype', 'glm', 'gamespy', 'c++_shared']
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None, 'Corrupt APK entry'
    for lib in required:
        entry = archive.getinfo(f'lib/arm64-v8a/lib{lib}.so')
        assert entry.file_size > 0, f'Empty library {lib}'
    assert archive.getinfo('classes.dex').file_size > 0, 'Missing Java code'
    assert archive.getinfo('AndroidManifest.xml').file_size > 0, 'Missing manifest'
print(f'Verified APK: {apk}, {apk.stat().st_size} bytes, all 10 native libraries present')
PY
(cd release && sha256sum GeneralsZH-LAN-release.apk > SHA256SUMS)
cat release/SHA256SUMS

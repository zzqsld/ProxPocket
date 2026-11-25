#!/usr/bin/env bash
# 编译 libpm3rrg_rdv4.so (Android arm64-v8a) 并拷贝到 app/src/main/jniLibs
# 用法: bash tools/build_pm3lib.sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
NDK="$SDK/ndk/27.2.12479018"
CMAKE="$SDK/cmake/3.22.1/bin/cmake.exe"
NINJA="$SDK/cmake/3.22.1/bin/ninja.exe"
SRC="$ROOT/third_party/rrg-tmp/client/experimental_lib"
BUILD="$SRC/build-android"

"$CMAKE" -B "$BUILD" -S "$SRC" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$NINJA" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-26 \
  -DCMAKE_C_FLAGS="-Wno-error=deprecated-declarations -Wno-deprecated-declarations -Wno-error=unused-but-set-variable -Wno-error=unused-variable -Wno-error=unused-function" \
  -DCMAKE_BUILD_TYPE=Release \
  -DSKIPQT=1 -DSKIPBT=1 -DSKIPPYTHON=1 -DSKIPPTHREAD=1 -DSKIPGD=1 -DSKIPREADLINE=1

"$CMAKE" --build "$BUILD" -j 8

mkdir -p "$ROOT/app/src/main/jniLibs/arm64-v8a"
cp "$BUILD/libpm3rrg_rdv4.so" "$ROOT/app/src/main/jniLibs/arm64-v8a/"
echo "==> libpm3rrg_rdv4.so -> app/src/main/jniLibs/arm64-v8a/"

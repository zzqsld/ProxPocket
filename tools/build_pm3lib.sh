#!/usr/bin/env bash
# 编译 libpm3rrg_rdv4.so (Android 全 ABI) 并拷贝到 app/src/main/jniLibs
# 用法: bash tools/build_pm3lib.sh [abi...]   （不带参数则编译全部四种 ABI）
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
NDK="$SDK/ndk/27.2.12479018"
CMAKE="$SDK/cmake/3.22.1/bin/cmake.exe"
NINJA="$SDK/cmake/3.22.1/bin/ninja.exe"
SRC="$ROOT/third_party/rrg-tmp/client/experimental_lib"

ABIS=("$@")
if [ ${#ABIS[@]} -eq 0 ]; then
  ABIS=(arm64-v8a armeabi-v7a x86 x86_64)
fi

for ABI in "${ABIS[@]}"; do
  BUILD="$SRC/build-$ABI"
  echo "==> [$ABI] configure"
  "$CMAKE" -B "$BUILD" -S "$SRC" -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=android-26 \
    -DCMAKE_C_FLAGS="-Wno-error=deprecated-declarations -Wno-deprecated-declarations -Wno-error=unused-but-set-variable -Wno-error=unused-variable -Wno-error=unused-function" \
    -DCMAKE_BUILD_TYPE=Release \
    -DSKIPQT=1 -DSKIPBT=1 -DSKIPPYTHON=1 -DSKIPPTHREAD=1 -DSKIPGD=1 -DSKIPREADLINE=1

  echo "==> [$ABI] build"
  "$CMAKE" --build "$BUILD" -j 8

  mkdir -p "$ROOT/app/src/main/jniLibs/$ABI"
  cp "$BUILD/libpm3rrg_rdv4.so" "$ROOT/app/src/main/jniLibs/$ABI/"
  echo "==> libpm3rrg_rdv4.so -> app/src/main/jniLibs/$ABI/"
done

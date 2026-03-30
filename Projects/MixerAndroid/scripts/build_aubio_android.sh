#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUBIO_SRC_DIR="${ROOT_DIR}/third_party/aubio/aubio-src"
AUBIO_BUILD_DIR="${ROOT_DIR}/third_party/aubio/build"
AUBIO_PREBUILT_DIR="${ROOT_DIR}/third_party/aubio/prebuilt"
ANDROID_API="${ANDROID_API:-26}"
ABIS=("armeabi-v7a" "arm64-v8a" "x86_64")

if [[ -z "${ANDROID_NDK_HOME:-}" && -z "${ANDROID_NDK_ROOT:-}" ]]; then
  echo "ANDROID_NDK_HOME or ANDROID_NDK_ROOT must be set."
  exit 1
fi

NDK_DIR="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT}}"
TOOLCHAIN_FILE="${NDK_DIR}/build/cmake/android.toolchain.cmake"
SDK_ROOT="$(cd "${NDK_DIR}/../.." && pwd)"

choose_cmake_bin() {
  if command -v cmake >/dev/null 2>&1; then
    local version
    version="$(cmake --version | head -n 1 | awk '{print $3}')"
    if [[ "${version}" =~ ^3\.(2[2-9]|[3-9][0-9])\. ]]; then
      echo "cmake"
      return
    fi
  fi

  local candidate
  candidate="$(find "${SDK_ROOT}/cmake" -maxdepth 3 -type f -name cmake 2>/dev/null | sort -V | tail -n 1 || true)"
  if [[ -n "${candidate}" ]]; then
    echo "${candidate}"
    return
  fi

  echo "Could not find a CMake >= 3.22 binary. Install Android SDK CMake (3.22.1+)." >&2
  exit 1
}

CMAKE_BIN="$(choose_cmake_bin)"

if [[ ! -f "${TOOLCHAIN_FILE}" ]]; then
  echo "Android NDK toolchain file not found at: ${TOOLCHAIN_FILE}"
  exit 1
fi

if [[ ! -d "${AUBIO_SRC_DIR}" ]]; then
  cat <<'EOF'
aubio source directory not found.
Expected: Projects/MixerAndroid/third_party/aubio/aubio-src

Download and extract aubio source first, for example:
  cd Projects/MixerAndroid/third_party/aubio
  curl -L -o aubio-0.4.9.tar.bz2 https://aubio.org/pub/aubio-0.4.9.tar.bz2
  tar -xjf aubio-0.4.9.tar.bz2
  mv aubio-0.4.9 aubio-src
EOF
  exit 1
fi

mkdir -p "${AUBIO_BUILD_DIR}" "${AUBIO_PREBUILT_DIR}"

build_with_cmake() {
  local abi="$1"
  local build_dir="${AUBIO_BUILD_DIR}/${abi}"
  local out_dir="${AUBIO_PREBUILT_DIR}/${abi}"

  rm -rf "${build_dir}"
  mkdir -p "${build_dir}" "${out_dir}"

  "${CMAKE_BIN}" \
    -S "${AUBIO_SRC_DIR}" \
    -B "${build_dir}" \
    -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN_FILE}" \
    -DANDROID_ABI="${abi}" \
    -DANDROID_PLATFORM="android-${ANDROID_API}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DAUBIO_BUILD_TESTS=OFF \
    -DAUBIO_BUILD_EXAMPLES=OFF

  "${CMAKE_BIN}" --build "${build_dir}" --config Release

  local lib_path
  lib_path="$(find "${build_dir}" -type f \( -name "libaubio.a" -o -name "aubio.a" \) | head -n 1)"
  if [[ -z "${lib_path}" ]]; then
    echo "Could not locate libaubio.a for ABI ${abi}."
    exit 1
  fi

  cp "${lib_path}" "${out_dir}/libaubio.a"
  echo "Built ${abi}: ${out_dir}/libaubio.a"
}

if [[ -f "${AUBIO_SRC_DIR}/CMakeLists.txt" ]]; then
  build_mode="cmake"
elif [[ -f "${AUBIO_SRC_DIR}/wscript" ]]; then
  build_mode="cmake-wrapper"
else
  echo "Unsupported aubio source layout at ${AUBIO_SRC_DIR}. Expected CMakeLists.txt or wscript."
  exit 1
fi

for abi in "${ABIS[@]}"; do
  if [[ "${build_mode}" == "cmake" ]]; then
    build_with_cmake "${abi}"
  else
    wrapper_dir="${AUBIO_BUILD_DIR}/cmake-wrapper"
    wrapper_file="${wrapper_dir}/CMakeLists.txt"
    mkdir -p "${wrapper_dir}"
    cat > "${wrapper_file}" <<EOF
cmake_minimum_required(VERSION 3.22.1)
project(aubio_wrapper C)

set(CMAKE_C_STANDARD 11)
set(AUBIO_SRC_DIR "${AUBIO_SRC_DIR}")
file(GLOB_RECURSE AUBIO_SRC_FILES "\${AUBIO_SRC_DIR}/src/*.c")
add_library(aubio STATIC \${AUBIO_SRC_FILES})
target_include_directories(aubio PUBLIC "\${AUBIO_SRC_DIR}/src")
target_compile_definitions(aubio PRIVATE
  NDEBUG=1
  HAVE_STDLIB_H=1
  HAVE_STDIO_H=1
  HAVE_MATH_H=1
  HAVE_STRING_H=1
  HAVE_ERRNO_H=1
  HAVE_LIMITS_H=1
  HAVE_STDARG_H=1
  HAVE_UNISTD_H=1
  HAVE_MEMCPY_HACKS=1
  HAVE_AUBIO_DOUBLE=0
)
EOF

    build_dir="${AUBIO_BUILD_DIR}/${abi}"
    out_dir="${AUBIO_PREBUILT_DIR}/${abi}"
    rm -rf "${build_dir}"
    mkdir -p "${build_dir}" "${out_dir}"

    "${CMAKE_BIN}" \
      -S "${wrapper_dir}" \
      -B "${build_dir}" \
      -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN_FILE}" \
      -DANDROID_ABI="${abi}" \
      -DANDROID_PLATFORM="android-${ANDROID_API}" \
      -DCMAKE_BUILD_TYPE=Release

    "${CMAKE_BIN}" --build "${build_dir}" --config Release

    lib_path="$(find "${build_dir}" -type f -name "libaubio.a" | head -n 1)"
    if [[ -z "${lib_path}" ]]; then
      echo "Could not locate libaubio.a for ABI ${abi}."
      exit 1
    fi

    cp "${lib_path}" "${out_dir}/libaubio.a"
    echo "Built ${abi}: ${out_dir}/libaubio.a"
  fi
done

echo "Aubio static libraries are ready in: ${AUBIO_PREBUILT_DIR}"

# Aubio For Android

This directory holds the pinned aubio source and Android static library artifacts used by `core/dsp`.

## Expected Layout

- `third_party/aubio/aubio-src/` -> unpacked upstream aubio source tree
- `third_party/aubio/prebuilt/<abi>/libaubio.a` -> generated static libs for Android ABIs
- `third_party/aubio/build/<abi>/` -> intermediate CMake build output

## Build

From `Projects/MixerAndroid`:

```bash
export ANDROID_NDK_HOME="$HOME/Android/Android_Studio_sdk/ndk/<version>"
./scripts/build_aubio_android.sh
```

The script targets:

- `armeabi-v7a`
- `arm64-v8a`
- `x86_64`

`core/dsp` JNI build links aubio only when `prebuilt/<abi>/libaubio.a` exists for the active ABI.

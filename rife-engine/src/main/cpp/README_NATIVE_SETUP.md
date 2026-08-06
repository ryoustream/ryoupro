# rife-engine native build - setup

This module does not vendor `ncnn` or `rife-ncnn-vulkan` (together they're
several hundred MB with submodules, and both already have their own proper
CMake build systems that are better reused than reimplemented).

## One-time setup

```bash
cd rife-engine/src/main/cpp
./setup_native_deps.sh
```

This clones:
- `Tencent/ncnn` (BSD-3-Clause) into `third_party/ncnn`
- `TNTwise/rife-ncnn-vulkan` (MIT, active fork of nihui/rife-ncnn-vulkan -
  the original has had no releases since 2022; TNTwise's fork is the
  actively maintained one and is what `.gitmodules` actually pins) into
  `third_party/rife-ncnn-vulkan`

Then, manually:
1. Confirm `RIFE::process()`'s signature in the vendored `rife.h` matches
   `rife_engine.cpp`'s assumption (this scaffold targets the v4.x API,
   which added a free `timestep` argument vs. the older hardcoded-0.5 v2 API).
2. Download a RIFE model (e.g. `rife-v4.6`) from the
   [rife-ncnn-vulkan releases page](https://github.com/TNTwise/rife-ncnn-vulkan/releases)
   and place it under `app/src/main/assets/rife_models/<name>/` - see
   `RifeModel.kt` for the expected folder naming.

## Build status of this scaffold

| Piece | Status |
|---|---|
| JNI bridge (`rife_jni.cpp`) | Complete, ready to compile once ncnn is present |
| `RifeEngine` NV12<->ncnn::Mat adapter | Real code path written against the v4.x API; **verify the exact `RIFE::process()` signature** against whatever tag `setup_native_deps.sh` pins before building |
| Mode A decode/encode/mux (Kotlin) | Complete (`RifeExportWorker.kt`), uses `android.media` APIs only, no native deps beyond RIFE itself |
| Mode B stream source (`mpv_rife_stream_source.cpp`) | Ring-buffer + callback skeleton is complete; the actual `AMediaCodec` decode-loop body is marked `TODO(fase-4a)` and, more importantly, **cannot be wired to mpv at all until the prerequisite in `/mpv-android-patch/` is applied** - the AAR this repo currently pulls (`SunnyVishnu3/mpvlibAndroid`) doesn't expose the hook it needs |

## Why CMake won't just fail without these

`CMakeLists.txt` compiles a stub build (`RIFE_ENGINE_HAS_NCNN=0` /
`RIFE_ENGINE_HAS_MPV_STREAM_CB=0`) when the third-party sources aren't
present, so a fresh clone of this branch still builds and the app still
runs - the Smooth Motion export screen will just report the engine as
unavailable (see `RifeCapabilityProbe.kt`) until setup is completed.

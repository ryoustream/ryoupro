# RIFE Smooth Motion integration - status & how to build

This patch adds a `:rife-engine` module to Nosved-Player implementing the
phased plan discussed for bringing SVP-style AI frame interpolation to this
player. It is a **scaffold**, not a finished feature: everything that could
be written and reasoned about without an Android device, GPU, and the actual
third-party native sources on hand has been written for real; everything
that genuinely depends on those is clearly marked below rather than faked.

## What's real and should compile once native deps are fetched

- `:rife-engine` Gradle module, wired into `settings.gradle.kts` and
  `app/build.gradle.kts`.
- JNI bridge (`rife_jni.cpp`) + `RifeEngine` C++ adapter around
  rife-ncnn-vulkan's `RIFE` class.
- Full Kotlin API surface: `RifeConfig`/`RifeModel`/`RifeScale`
  (multi-model, scale, TTA, UHD, GPU/CPU - the "full API" originally asked
  for, not one hardcoded setting).
- **Mode A (batch/export)**: `RifeExportWorker` is a complete
  decode→interpolate→encode→mux pipeline against `android.media` APIs only,
  wrapped in a `CoroutineWorker` with foreground-notification + progress
  reporting, exposed via `RifeExportManager`.
- New UI: "Smooth Motion (RIFE)" entry in the Tools screen →
  `SmoothMotionExportScreen` + `SmoothMotionViewModel`, following this
  repo's existing screen/ViewModel conventions exactly (plain
  `AndroidViewModel`, no DI framework - matches `SettingsViewModel` etc.).
- `RifeCapabilityProbe`: gates Mode B behind a Vulkan-version + RAM
  heuristic calibrated against SVPlayer's own published requirement
  (Android 9+, Snapdragon 865-class recommended).

## What's intentionally NOT done here, and why

| Piece | Status | Why |
|---|---|---|
| `third_party/ncnn`, `third_party/rife-ncnn-vulkan` | Not vendored | Hundreds of MB combined; `rife-engine/src/main/cpp/setup_native_deps.sh` fetches them |
| RIFE model weights | Not bundled | 15-25MB each; see that script's printed instructions |
| **Mode B (real-time in-player smoothing)** | Ring-buffer/callback skeleton written, decode loop body is `TODO(fase-4a)`, **cannot be wired to mpv at all yet** | The prebuilt `mpvlib.aar` this repo consumes doesn't expose `mpv_stream_cb_add_ro` or a raw `mpv_handle*` - see `/mpv-android-patch/README.md`. This was discovered by actually inspecting `MPVPlayerEngine.kt` and the AAR's provenance, not assumed. |
| Actual compilation/on-device testing | Not done | No Android device/NDK toolchain available in the environment this was authored in |

## Known gaps to fix before this is production-quality

1. **`RifeEngine::process()` signature** (C++): written against the v4.x
   `rife-ncnn-vulkan` API (adds a free `timestep` arg). Confirm this matches
   whatever tag `setup_native_deps.sh` actually pins before building.
2. **`toNv12()` stride handling** (Kotlin, in `RifeExportWorker`): handles
   the common tightly-packed case and the `KEY_STRIDE`/`KEY_SLICE_HEIGHT`
   padded case, but hasn't been validated against every OEM decoder's
   quirks - a known messy corner of `MediaCodec` on Android in general.
3. **Mode B decode loop** (`mpv_rife_stream_source.cpp`): the
   `AMediaExtractor`/`AMediaCodec` producer body is a placeholder
   (`gotFrame = false`) - real implementation is Fase 4a work once the
   mpv-android-patch prerequisite exists to test against.
4. Seeking in Mode B is unimplemented (`cb_seek` returns -1). This mirrors a
   real limitation SVP itself has (brief "reinit" pause on seek), not a
   shortcut unique to this scaffold - but it still needs implementing.

Fixed since the first version of this scaffold: the `MediaMuxer` audio-track
was previously added *after* `muxer.start()` had already been triggered by
the video track, which `MediaMuxer` rejects at runtime. `RifeExportWorker`
now adds the audio track (its format is known upfront) before the video
track/`start()` call, which happens once the encoder reports its output
format. Also fixed: `AndroidManifest.xml` was missing the
`FOREGROUND_SERVICE_DATA_SYNC` permission that `RifeExportWorker`'s
foreground notification needs on API 34+ (targetSdk=36 enforces this).

## Licensing / attribution

Everything pulled in is MIT or BSD-3-Clause and compatible with
Nosved-Player's own MIT license:

- RIFE (hzwer/Practical-RIFE) - MIT
- rife-ncnn-vulkan (nihui) - MIT
- ncnn (Tencent) - BSD-3-Clause

Add a NOTICE entry crediting these three before shipping a release build
that includes them (a full NOTICE file wasn't added here since this repo
doesn't currently have a root LICENSE/NOTICE file to extend consistently -
add both together).

## Build order

1. `cd rife-engine/src/main/cpp && ./setup_native_deps.sh`
2. Drop at least one RIFE model under `app/src/main/assets/rife_models/`
3. Gradle sync (pulls in the new `:rife-engine` module + `android-library`
   plugin alias added to `gradle/libs.versions.toml`)
4. Build & install - "Smooth Motion (RIFE)" should appear under Tools
5. Mode B only: follow `/mpv-android-patch/README.md` first

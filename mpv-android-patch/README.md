# Mode B prerequisite: patching mpvlib

## Why this exists

Nosved-Player consumes a **prebuilt** `mpvlib.aar`
(`app/libs/Donwlod-Mpvlibs.md` points at
`github.com/SunnyVishnu3/mpvlibAndroid/releases`, itself built from
`mpv-android/mpv-android`'s JNI bridge). That AAR's `is.xyz.mpv.MPVLib`
Java class exposes a property/command-oriented surface
(`setOptionString`, `setPropertyString`, `command`, `observeProperty`, ...)
but does **not** expose:
- the raw `mpv_handle*` libmpv itself hands back from `mpv_create()`, or
- a wrapper around `mpv_stream_cb_add_ro()`

Real-time smoothing (Mode B, see `RifeFrameSource.kt`) needs one of those two
things to register a custom "rife://" stream source the way SVP hijacks
mpv's decode stage on desktop (there, via a generated VapourSynth script
instead - see the chat history / RIFE_INTEGRATION_README.md for why that
specific mechanism doesn't port to Android). Nothing else in this repo can
reach the handle without this patch; there's no reflection or public JNI
symbol trick that gets around it safely.

**Mode A (batch/export interpolation) does NOT need any of this** - it's
pure `android.media` + our own JNI, fully independent of mpvlib. Only skip
this if you specifically want Mode B.

## What to actually do

1. Fork whichever mpv-android variant you want to build from - the original
   `mpv-android/mpv-android`, or `SunnyVishnu3/mpvlibAndroid`'s own source if
   it publishes one (its GitHub Releases page is binary-only as far as this
   analysis could confirm; check whether the org has a companion source
   repo before starting from scratch against upstream `mpv-android/mpv-android`).
2. Locate the JNI bridge source (in upstream `mpv-android`, this is the
   `buildscripts/` cross-compiled `libmpv.so` plus a JNI glue file exposing
   `Java_is_xyz_mpv_MPVLib_*` symbols - filename varies by fork/version, grep
   for `Java_is_xyz_mpv_MPVLib_create` to find it).
3. Apply `0001-add-rife-stream-cb-bridge.patch` in this folder - it's a
   **template**, not a guaranteed-clean patch: line numbers and the exact
   surrounding code will differ per fork/version. Adapt it to whatever you
   find in step 2.
4. Rebuild `libmpv.so` + the AAR using that fork's existing build scripts
   (this is the expensive step - full mpv+ffmpeg NDK cross-compile, typically
   30-90 minutes on a modern machine, per mpv-android's own build docs).
5. Copy the new AAR into `app/libs/mpvlib.aar`, replacing the stock one.
6. Copy `<mpv-source>/libmpv/client.h` and a `stream_cb.h` matching the exact
   mpv version you built into `rife-engine/src/main/cpp/third_party/mpv-include/mpv/`
   so `mpv_rife_stream_source.cpp` compiles for real
   (`RIFE_ENGINE_HAS_MPV_STREAM_CB=1`).
7. Update `RifeFrameSource.getRawMpvHandle()` to call the new
   `MPVLib.getRawHandle()` you added, instead of returning `null`.

## License note

`mpv-android` is GPLv2+ with LGPLv2.1+ parts. Nosved-Player already links
against a prebuilt build of it today (this predates this patch, not
introduced by it) - if you weren't already treating the app as subject to
that license's linking terms, now is a good time to confirm with whoever
maintains Nosved-Player's licensing posture before shipping a build that
includes a self-compiled variant.

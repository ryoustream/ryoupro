// Mode B - real-time in-player smoothing.
//
// This is the Android equivalent of what desktop SVP does by handing mpv a
// generated VapourSynth (.vpy) script instead of the real file: we hand mpv
// a virtual "rife://" stream via mpv_stream_cb_add_ro() that decodes the
// source, runs it through RifeEngine, and emits already-interpolated NV12
// frames at 2x/3x the original fps. mpv opens it with
// --demuxer=rawvideo and never knows RIFE is involved.
//
// PREREQUISITE: see /mpv-android-patch/README.md. The stock mpvlib.aar this
// repo consumes does not expose mpv_stream_cb_add_ro or a raw mpv_handle*,
// so nothing in this file can be wired up until that patch lands. Until
// then this compiles as an inert stub (RIFE_ENGINE_HAS_MPV_STREAM_CB=0) so
// the rest of the module (Mode A / batch export) is unaffected.
//
// Known inherent limitation (same as desktop SVP, not unique to this impl):
// seeking requires tearing down and restarting the decode+interpolate
// pipeline at the new position, which costs a brief re-buffering pause.

#include <android/log.h>

#define LOG_TAG "RifeStreamSource"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if RIFE_ENGINE_HAS_MPV_STREAM_CB

#include <mpv/stream_cb.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "rife_engine.h"

namespace {

// A small lock-free-ish ring of pre-interpolated NV12 frames. The producer
// thread (decode + RIFE) fills it; mpv's stream read() callback drains it.
// Sized conservatively - each slot is a full frame, so this is
// frameBytes * kRingSlots of memory (e.g. 1080p NV12 ~3.1MB/slot).
constexpr int kRingSlots = 6;

struct RifeStream {
    std::string sourcePath;
    int width = 0;
    int height = 0;
    int64_t frameBytes = 0;

    AMediaExtractor* extractor = nullptr;
    AMediaCodec* codec = nullptr;
    RifeEngine engine;

    std::vector<std::vector<uint8_t>> ring;
    std::atomic<int> ringHead{0}; // producer writes here
    std::atomic<int> ringTail{0}; // consumer reads here
    std::atomic<bool> eof{false};
    std::atomic<bool> stopRequested{false};
    std::thread producerThread;
    std::mutex producerMutex;

    int64_t readOffsetInCurrentFrame = 0;
    int64_t bytesEmittedTotal = 0;

    ~RifeStream() {
        stopRequested = true;
        if (producerThread.joinable()) producerThread.join();
        if (codec) { AMediaCodec_delete(codec); }
        if (extractor) { AMediaExtractor_delete(extractor); }
    }
};

// Decodes source frames pairwise and pushes RIFE(A,B) + B into the ring.
// This is the direct analogue of SVP's Analyse()+SmoothFps() VapourSynth
// filter chain, just implemented against NDK media APIs + RifeEngine
// instead of VapourSynth + SVPflow/MVTools.
void producerLoop(RifeStream* s) {
    std::vector<uint8_t> prevFrame(s->frameBytes);
    bool havePrev = false;

    // NOTE: this is intentionally a straightforward blocking decode loop, not
    // a fully async MediaCodec callback pipeline. Good enough to validate
    // correctness end-to-end; revisit for the async API if Fase 1 profiling
    // shows the decode stage (not RIFE inference) is the bottleneck.
    while (!s->stopRequested.load()) {
        const int nextSlot = (s->ringHead.load() + 1) % kRingSlots;
        if (nextSlot == s->ringTail.load()) {
            // Ring full - consumer (mpv) is behind. Back off briefly.
            std::this_thread::sleep_for(std::chrono::milliseconds(2));
            continue;
        }

        // --- pull + decode next source frame via AMediaCodec ---
        // (Full decode-loop bookkeeping - input buffer queuing, output
        // buffer draining, format-changed handling - omitted here for
        // brevity; follow the standard NdkMediaCodec decode loop pattern.
        // TODO(fase-4a): fill in against the real extractor/codec instance.)
        std::vector<uint8_t> currentFrame(s->frameBytes);
        const bool gotFrame = false; // placeholder until decode loop is wired

        if (!gotFrame) {
            s->eof = true;
            return;
        }

        auto& slot = s->ring[nextSlot];
        if (havePrev) {
            const int rc = s->engine.interpolate(prevFrame.data(), currentFrame.data(),
                                                  s->width, s->height, 0.5f, slot.data());
            if (rc != 0) {
                LOGE("interpolate() failed rc=%d, falling back to frame repeat", rc);
                std::memcpy(slot.data(), currentFrame.data(), s->frameBytes);
            }
            s->ringHead.store(nextSlot);
        }
        prevFrame.swap(currentFrame);
        havePrev = true;

        const int frameSlot = (s->ringHead.load() + 1) % kRingSlots;
        std::memcpy(s->ring[frameSlot].data(), prevFrame.data(), s->frameBytes);
        s->ringHead.store(frameSlot);
    }
}

int64_t cb_read(void* cookie, char* buf, uint64_t nbytes) {
    auto* s = static_cast<RifeStream*>(cookie);
    uint64_t written = 0;
    while (written < nbytes) {
        const int tail = s->ringTail.load();
        if (tail == s->ringHead.load()) {
            if (s->eof.load()) break;
            std::this_thread::sleep_for(std::chrono::milliseconds(2));
            continue;
        }
        auto& slot = s->ring[tail];
        const int64_t available = s->frameBytes - s->readOffsetInCurrentFrame;
        const int64_t toCopy = std::min<int64_t>(available, nbytes - written);
        std::memcpy(buf + written, slot.data() + s->readOffsetInCurrentFrame, toCopy);
        written += toCopy;
        s->readOffsetInCurrentFrame += toCopy;
        if (s->readOffsetInCurrentFrame >= s->frameBytes) {
            s->readOffsetInCurrentFrame = 0;
            s->ringTail.store((tail + 1) % kRingSlots);
        }
    }
    s->bytesEmittedTotal += static_cast<int64_t>(written);
    return static_cast<int64_t>(written);
}

int64_t cb_size(void* cookie) {
    // Unknown/unbounded for a live-generated stream - mpv treats a negative
    // return as "size not known", which is the correct answer here.
    (void)cookie;
    return -1;
}

int64_t cb_seek(void* cookie, int64_t offset) {
    // Re-seeking means tearing down and restarting the producer at a new
    // source timestamp - exactly SVP's own "reinit" pause on seek.
    // TODO(fase-4a): translate byte offset -> source PTS using frameBytes
    // and framerate, AMediaExtractor_seekTo(), restart producer thread.
    (void)cookie;
    (void)offset;
    return -1; // not yet implemented - seeking disabled in this skeleton
}

void cb_close(void* cookie) {
    delete static_cast<RifeStream*>(cookie);
}

int cb_open(void* userData, const char* uri, mpv_stream_cb_info* info) {
    // Expected URI shape: rife://<urlencoded source path>?w=<width>&h=<height>&fps=<fps>
    // Kotlin side (RifeFrameSource.kt) is responsible for building this URI
    // and pre-flighting the capability probe before ever calling
    // mpv_command(["loadfile", uri]).
    auto* s = new RifeStream();
    // TODO(fase-4a): parse uri into s->sourcePath/width/height, open
    // AMediaExtractor + AMediaCodec, call s->engine.init(...), then start
    // s->producerThread = std::thread(producerLoop, s).
    (void)userData;
    (void)uri;

    s->frameBytes = static_cast<int64_t>(s->width) * s->height * 3 / 2;
    if (s->frameBytes <= 0) {
        LOGE("cb_open: invalid dimensions, refusing to open %s", uri);
        delete s;
        return -1;
    }
    s->ring.resize(kRingSlots, std::vector<uint8_t>(s->frameBytes));

    info->cookie = s;
    info->read_fn = cb_read;
    info->seek_fn = cb_seek;
    info->size_fn = cb_size;
    info->close_fn = cb_close;
    info->cancel_fn = nullptr;
    return 0;
}

} // namespace

extern "C" int rife_register_mpv_stream_source(void* mpvHandle) {
    // mpvHandle must be the raw mpv_handle* obtained from the patched
    // MPVLib (see mpv-android-patch/). Signature kept as void* here so this
    // translation unit doesn't need to fight over which client.h gets
    // included first when this is eventually linked into the patched build.
    return mpv_stream_cb_add_ro(
        reinterpret_cast<mpv_handle*>(mpvHandle), "rife", nullptr, cb_open);
}

#else // !RIFE_ENGINE_HAS_MPV_STREAM_CB

extern "C" int rife_register_mpv_stream_source(void* /*mpvHandle*/) {
    LOGI("Mode B unavailable in this build (mpv-android-patch prerequisite "
         "not applied). Batch export (Mode A) is unaffected.");
    return -100;
}

#endif

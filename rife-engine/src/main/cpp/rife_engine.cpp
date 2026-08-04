#include "rife_engine.h"
#include <android/log.h>

#define LOG_TAG "RifeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if RIFE_ENGINE_HAS_NCNN

// rife-ncnn-vulkan has shipped a couple of slightly different signatures
// for RIFE::process() across its v2/v4 branches (v4 added the free
// `timestep` argument for non-2x factors; older tags hardcode 0.5f).
// VERIFIED against nihui/rife-ncnn-vulkan HEAD (src/rife.h) on 2026-07-31:
//   int process(const ncnn::Mat& in0image, const ncnn::Mat& in1image,
//               float timestep, ncnn::Mat& outimage) const;
// matches exactly what's called below - no change needed if
// setup_native_deps.sh still pins a v4-era tag.
#include "rife.h"
#include "mat.h"
#include "gpu.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace {

// ncnn provides yuv420sp2rgb_nv12() for the INPUT direction (verified in
// ncnn/src/mat.h) but has no reverse rgb2yuv420sp - image-to-network is a
// far more common need than network-to-video, so ncnn simply never needed
// one. RIFE's own CLI tool only ever writes PNG/WebP output on desktop.
// matToNv12() below is hand-written: standard BT.601 fixed-point
// coefficients (the same family of "fast approximate" integer math ncnn's
// own yuv420sp2rgb_nv12 uses, per its header comment), not bit-exact to any
// particular reference implementation but visually correct and standard
// practice for this kind of round-trip.

ncnn::Mat nv12ToMat(const uint8_t* nv12, int width, int height) {
    std::vector<uint8_t> rgb(static_cast<size_t>(width) * height * 3);
    ncnn::yuv420sp2rgb_nv12(nv12, width, height, rgb.data());
    return ncnn::Mat::from_pixels(rgb.data(), ncnn::Mat::PIXEL_RGB, width, height);
}

inline uint8_t clamp8(int v) { return static_cast<uint8_t>(std::clamp(v, 0, 255)); }

void matToNv12(const ncnn::Mat& mat, uint8_t* outNv12, int width, int height) {
    // rife-ncnn-vulkan's RIFE::process()/process_v4() *signature* is
    // pinned and re-verified per tag (see comment above this namespace),
    // but the *value range* of the returned outimage Mat is not part of
    // that contract - upstream src/rife.cpp shows postproc denormalizing
    // back to pixel range internally, but that isn't guaranteed identical
    // across the v2/v4 code paths or ncnn versions. If outimage were ever
    // handed back as normalized [0,1] float instead of pixel-range
    // [0,255], to_pixels() below would silently round nearly every value
    // to 0 or 1 - no crash, just a near-uniform near-black frame,
    // indistinguishable at a glance from a real black frame. That exactly
    // matches the reported symptom: every original frame fine, every
    // RIFE-interpolated frame black (measured ~(1,1,1) RGB, not the
    // uninitialized-buffer green you'd get from a memory bug). Detect it
    // defensively by sampling raw values before trusting to_pixels(), and
    // log which branch fired so the next trace confirms it either way.
    ncnn::Mat pixelRangeMat = mat;
    {
        float sampleMax = 0.f;
        for (int c = 0; c < mat.c; ++c) {
            const float* p = mat.channel(c);
            const int n = mat.w * mat.h;
            const int stride = std::max(1, n / 64); // ~64 samples/channel, enough to tell 0..1 from 0..255
            for (int i = 0; i < n; i += stride) {
                sampleMax = std::max(sampleMax, std::fabs(p[i]));
            }
        }
        if (sampleMax <= 2.0f) {
            LOGI("matToNv12: RIFE output looks normalized (sampleMax=%.4f, expected ~0-255) "
                 "- rescaling x255 before to_pixels() to avoid a near-black frame", sampleMax);
            pixelRangeMat = mat.clone();
            for (int c = 0; c < pixelRangeMat.c; ++c) {
                float* p = pixelRangeMat.channel(c);
                const int n = pixelRangeMat.w * pixelRangeMat.h;
                for (int i = 0; i < n; ++i) p[i] *= 255.f;
            }
        }
    }

    std::vector<uint8_t> rgb(static_cast<size_t>(width) * height * 3);
    pixelRangeMat.to_pixels(rgb.data(), ncnn::Mat::PIXEL_RGB);

    uint8_t* yPlane = outNv12;
    uint8_t* uvPlane = outNv12 + static_cast<size_t>(width) * height;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const uint8_t* p = &rgb[(static_cast<size_t>(y) * width + x) * 3];
            const int r = p[0], g = p[1], b = p[2];
            const int yVal = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
            yPlane[static_cast<size_t>(y) * width + x] = clamp8(yVal);
        }
    }
    // 4:2:0 chroma subsampling - one U,V sample per 2x2 luma block, taken
    // from the top-left pixel of each block (matches what most fast NV12
    // encoders do rather than averaging all 4, negligible quality
    // difference for interpolated video frames).
    for (int y = 0; y < height; y += 2) {
        for (int x = 0; x < width; x += 2) {
            const uint8_t* p = &rgb[(static_cast<size_t>(y) * width + x) * 3];
            const int r = p[0], g = p[1], b = p[2];
            const int uVal = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
            const int vVal = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
            const size_t uvIdx = static_cast<size_t>(y / 2) * width + x;
            uvPlane[uvIdx] = clamp8(uVal);
            uvPlane[uvIdx + 1] = clamp8(vVal);
        }
    }
}
} // namespace

int RifeEngine::init(const InitParams& params) {
    // REQUIRED once before any ncnn::get_gpu_count()/get_gpu_device() call
    // works at all - confirmed by checking rife-ncnn-vulkan's own
    // src/main.cpp, which calls this before constructing any RIFE instance.
    // Missing this call was half of a real crash (SIGSEGV, fault address
    // 0x0, inside the first RifeEngine::interpolate() call) - Vulkan device
    // state was never actually initialized even though RIFE's constructor
    // itself completed and returned a handle successfully.
    static bool gpuInstanceCreated = false;
    if (!gpuInstanceCreated) {
        if (ncnn::create_gpu_instance() != 0) {
            LOGE("ncnn::create_gpu_instance() failed - no usable Vulkan driver");
            return -3;
        }
        gpuInstanceCreated = true;
    }

    // The OTHER half of the same crash: rife-ncnn-vulkan's own RIFE
    // constructor treats gpuid==-1 as "CPU only, no Vulkan device at all"
    // (vkdev = gpuid == -1 ? 0 : ncnn::get_gpu_device(gpuid); - verified by
    // reading src/rife.cpp directly), which is the OPPOSITE of what this
    // module's own RifeConfig.kt documents ("-1 = auto-pick fastest Vulkan
    // device"). Translate this module's sentinel values into what
    // rife-ncnn-vulkan actually expects, rather than passing -1 straight
    // through and silently running with vkdev=null.
    int resolvedGpuId;
    if (params.gpuId == -1) {
        resolvedGpuId = ncnn::get_default_gpu_index();
    } else if (params.gpuId == -2) {
        resolvedGpuId = -1; // rife-ncnn-vulkan's real "CPU only" sentinel
    } else {
        resolvedGpuId = params.gpuId; // explicit device index, pass through
    }
    LOGI("gpuId resolved: requested=%d -> rife-ncnn-vulkan gpuid=%d (gpu_count=%d)",
         params.gpuId, resolvedGpuId, ncnn::get_gpu_count());

    auto* rife = new RIFE(resolvedGpuId, params.ttaSpatial, params.ttaTemporal,
                           params.uhdMode, params.numThreads,
                           /*rife_v2=*/false, /*rife_v4=*/true);
    if (rife->load(params.modelDir.c_str()) != 0) {
        LOGE("Failed to load RIFE model from %s", params.modelDir.c_str());
        delete rife;
        return -1;
    }
    backendHandle_ = rife;
    ready_ = true;
    LOGI("RIFE engine ready (gpuid=%d, uhd=%d)", resolvedGpuId, params.uhdMode);
    return 0;
}

int RifeEngine::interpolate(const uint8_t* frameA, const uint8_t* frameB,
                             int width, int height, float timestep,
                             uint8_t* outFrame) {
    if (!ready_) return -2;
    auto* rife = static_cast<RIFE*>(backendHandle_);

    ncnn::Mat matA = nv12ToMat(frameA, width, height);
    ncnn::Mat matB = nv12ToMat(frameB, width, height);
    ncnn::Mat matOut;

    const int rc = rife->process(matA, matB, timestep, matOut);
    if (rc != 0) {
        LOGE("RIFE::process failed rc=%d", rc);
        return rc;
    }
    matToNv12(matOut, outFrame, width, height);
    return 0;
}

void RifeEngine::release() {
    if (backendHandle_) {
        delete static_cast<RIFE*>(backendHandle_);
        backendHandle_ = nullptr;
    }
    ready_ = false;
}

RifeEngine::~RifeEngine() { release(); }

#else // !RIFE_ENGINE_HAS_NCNN - stub build, see README_NATIVE_SETUP.md

int RifeEngine::init(const InitParams&) {
    LOGE("rife-engine built WITHOUT ncnn/rife-ncnn-vulkan sources present. "
         "Run setup_native_deps.sh, then rebuild. Falling back to unavailable.");
    ready_ = false;
    return -100;
}

int RifeEngine::interpolate(const uint8_t*, const uint8_t*, int, int, float, uint8_t*) {
    return -100;
}

void RifeEngine::release() { ready_ = false; }

RifeEngine::~RifeEngine() = default;

#endif

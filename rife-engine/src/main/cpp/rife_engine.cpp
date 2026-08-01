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
#include <algorithm>
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
    std::vector<uint8_t> rgb(static_cast<size_t>(width) * height * 3);
    mat.to_pixels(rgb.data(), ncnn::Mat::PIXEL_RGB);

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
    auto* rife = new RIFE(params.gpuId, params.ttaSpatial, params.ttaTemporal,
                           params.uhdMode, params.numThreads,
                           /*rife_v2=*/false, /*rife_v4=*/true);
    if (rife->load(params.modelDir.c_str()) != 0) {
        LOGE("Failed to load RIFE model from %s", params.modelDir.c_str());
        delete rife;
        return -1;
    }
    backendHandle_ = rife;
    ready_ = true;
    LOGI("RIFE engine ready (gpuId=%d, uhd=%d)", params.gpuId, params.uhdMode);
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

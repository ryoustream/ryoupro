#include "rife_engine.h"
#include <android/log.h>

#define LOG_TAG "RifeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if RIFE_ENGINE_HAS_NCNN

// NOTE: rife-ncnn-vulkan has shipped a couple of slightly different
// signatures for RIFE::process() across its v2/v4 branches (v4 added the
// free `timestep` argument for non-2x factors; older tags hardcode 0.5f).
// Whichever tag setup_native_deps.sh pins, CONFIRM this signature against
// the vendored third_party/rife-ncnn-vulkan/src/rife.h before building -
// this file targets the v4.x signature.
#include "rife.h"
#include <ncnn/mat.h>

namespace {
// NV12 -> planar RGB float ncnn::Mat expected by RIFE::process(), and back.
// rife-ncnn-vulkan's own CLI does this via stb_image/webp on desktop; on
// Android we go straight from the decoder's NV12 buffer to avoid an extra
// PNG-shaped round trip.
ncnn::Mat nv12ToMat(const uint8_t* nv12, int width, int height) {
    return ncnn::Mat::from_pixels(nv12, ncnn::Mat::PIXEL_YUV420SP2BGR, width, height);
}

void matToNv12(const ncnn::Mat& mat, uint8_t* outNv12, int width, int height) {
    mat.to_pixels(outNv12, ncnn::Mat::PIXEL_BGR2YUV420SP);
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

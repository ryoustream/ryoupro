#ifndef RIFE_ENGINE_H
#define RIFE_ENGINE_H

#include <cstdint>
#include <string>

// Thin, stable adapter around whichever rife-ncnn-vulkan version ends up
// vendored under third_party/. Kept deliberately narrow (init / interpolate /
// release) so rife_jni.cpp never has to know about ncnn::Mat, GPU instance
// lifecycle, etc.
//
// Pixel format contract: NV12 (Y plane + interleaved VU plane), matching
// what Android's MediaCodec decoder emits by default (COLOR_FormatYUV420Flexible)
// and what mpv's --demuxer=rawvideo expects for fourcc NV12. Converting at the
// JNI boundary instead of inside the engine keeps this class GPU-format agnostic.
class RifeEngine {
public:
    struct InitParams {
        std::string modelDir;   // path to a single extracted RIFE model folder
        int gpuId = -1;         // -1 = auto-pick fastest Vulkan device, -2 = CPU
        bool ttaSpatial = false;
        bool ttaTemporal = false;
        bool uhdMode = false;   // rife-UHD tiling path for >=2K sources
        int numThreads = 2;
    };

    RifeEngine() = default;
    ~RifeEngine();

    RifeEngine(const RifeEngine&) = delete;
    RifeEngine& operator=(const RifeEngine&) = delete;

    // Returns 0 on success, negative mpv/ncnn-style error code otherwise.
    int init(const InitParams& params);

    // frameA/frameB: NV12 buffers of size width*height*3/2.
    // timestep: 0.5 for a single mid-frame (2x), or 1/3, 2/3 for 3x, etc.
    // outFrame: caller-allocated buffer, same size as inputs.
    int interpolate(const uint8_t* frameA, const uint8_t* frameB,
                     int width, int height, float timestep,
                     uint8_t* outFrame);

    void release();

    bool isReady() const { return ready_; }

private:
    bool ready_ = false;
    void* backendHandle_ = nullptr; // opaque pointer to vendored RIFE instance
};

#endif // RIFE_ENGINE_H

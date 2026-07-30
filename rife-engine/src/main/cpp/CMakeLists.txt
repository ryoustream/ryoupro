cmake_minimum_required(VERSION 3.22.1)

project("rife_engine_native" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Third-party sources (NOT vendored in this patch - see README_NATIVE_SETUP.md)
#
# Expected layout after running setup_native_deps.sh (documented in the
# README next to this file):
#
#   third_party/ncnn/                <- Tencent/ncnn, BSD-3-Clause
#   third_party/rife-ncnn-vulkan/     <- nihui/rife-ncnn-vulkan, MIT
#
# Both are pulled in as git submodules so their own CMake build files are
# reused as-is instead of being re-implemented here.
# ---------------------------------------------------------------------------
set(THIRD_PARTY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party)

if(EXISTS ${THIRD_PARTY_DIR}/ncnn/CMakeLists.txt)
    set(NCNN_VULKAN ON CACHE BOOL "" FORCE)
    set(NCNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(NCNN_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(NCNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
    add_subdirectory(${THIRD_PARTY_DIR}/ncnn ncnn_build)
    set(RIFE_DEPS_AVAILABLE TRUE)
else()
    message(WARNING
        "third_party/ncnn not found - building rife-engine in STUB mode. "
        "Run rife-engine/src/main/cpp/setup_native_deps.sh first, then "
        "re-sync Gradle. See README_NATIVE_SETUP.md.")
    set(RIFE_DEPS_AVAILABLE FALSE)
endif()

find_package(Vulkan)

# ---------------------------------------------------------------------------
# Mode B (real-time in-player smoothing) prerequisite.
#
# mpv_stream_cb_add_ro() is a real libmpv C API (see mpv/stream_cb.h), but
# the mpvlib.aar this project consumes (github.com/SunnyVishnu3/mpvlibAndroid)
# does NOT expose it through MPVLib.java, and no other component in this repo
# can reach the raw mpv_handle* it would need. This is NOT buildable against
# the stock AAR - see /mpv-android-patch/README.md at the repo root for the
# upstream patch this depends on. Until that patch exists and its headers are
# copied to third_party/mpv-include/, this file compiles as an inert stub.
# ---------------------------------------------------------------------------
if(EXISTS ${THIRD_PARTY_DIR}/mpv-include/mpv/stream_cb.h)
    set(RIFE_HAS_MPV_STREAM_CB TRUE)
else()
    message(WARNING
        "third_party/mpv-include/mpv/stream_cb.h not found - Mode B "
        "(RifeFrameSource real-time hook) compiled as an inert stub. "
        "This requires the mpv-android-patch/ prerequisite. Mode A "
        "(batch export) is unaffected.")
    set(RIFE_HAS_MPV_STREAM_CB FALSE)
endif()

add_library(rife_engine SHARED
    rife_jni.cpp
    rife_engine.cpp
    mpv_rife_stream_source.cpp
)

target_include_directories(rife_engine PRIVATE
    ${THIRD_PARTY_DIR}/rife-ncnn-vulkan/src
    ${THIRD_PARTY_DIR}/ncnn/src
    ${THIRD_PARTY_DIR}/mpv-include
)

if(RIFE_HAS_MPV_STREAM_CB)
    target_compile_definitions(rife_engine PRIVATE RIFE_ENGINE_HAS_MPV_STREAM_CB=1)
else()
    target_compile_definitions(rife_engine PRIVATE RIFE_ENGINE_HAS_MPV_STREAM_CB=0)
endif()

if(RIFE_DEPS_AVAILABLE)
    target_compile_definitions(rife_engine PRIVATE RIFE_ENGINE_HAS_NCNN=1)
    target_link_libraries(rife_engine PRIVATE ncnn)
else()
    target_compile_definitions(rife_engine PRIVATE RIFE_ENGINE_HAS_NCNN=0)
endif()

target_link_libraries(rife_engine PRIVATE
    android
    log
    mediandk
)

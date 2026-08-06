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
#   third_party/rife-ncnn-vulkan/     <- TNTwise/rife-ncnn-vulkan (active fork of nihui's, MIT)
#
# Both are pulled in as git submodules so their own CMake build files are
# reused as-is instead of being re-implemented here.
# ---------------------------------------------------------------------------
set(THIRD_PARTY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party)

if(EXISTS ${THIRD_PARTY_DIR}/ncnn/CMakeLists.txt AND EXISTS ${THIRD_PARTY_DIR}/rife-ncnn-vulkan/src/rife.cpp)
    set(NCNN_VULKAN ON CACHE BOOL "" FORCE)
    set(NCNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(NCNN_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(NCNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
    add_subdirectory(${THIRD_PARTY_DIR}/ncnn ncnn_build)
    set(RIFE_DEPS_AVAILABLE TRUE)

    # -----------------------------------------------------------------------
    # rife-ncnn-vulkan's RIFE class (rife.cpp/warp.cpp) uses hand-written
    # Vulkan compute shaders, compiled to SPIR-V and embedded as C headers at
    # build time - rife-ncnn-vulkan/src/CMakeLists.txt does this via a
    # rife_add_shader() macro + generate_shader_comp_header.cmake script.
    # Reused here verbatim (pointed at the vendored submodule path) rather
    # than reimplemented, since getting 17 shader names/order wrong is an
    # easy way to end up with a subtly broken build.
    # -----------------------------------------------------------------------
    set(RIFE_SRC_DIR ${THIRD_PARTY_DIR}/rife-ncnn-vulkan/src)
    include_directories(${CMAKE_CURRENT_BINARY_DIR})

    macro(rife_add_shader SHADER_SRC)
        get_filename_component(SHADER_SRC_NAME_WE ${SHADER_SRC} NAME_WE)
        set(SHADER_COMP_HEADER ${CMAKE_CURRENT_BINARY_DIR}/${SHADER_SRC_NAME_WE}.comp.hex.h)
        add_custom_command(
            OUTPUT ${SHADER_COMP_HEADER}
            COMMAND ${CMAKE_COMMAND} -DSHADER_SRC=${RIFE_SRC_DIR}/${SHADER_SRC} -DSHADER_COMP_HEADER=${SHADER_COMP_HEADER} -P "${RIFE_SRC_DIR}/generate_shader_comp_header.cmake"
            DEPENDS ${RIFE_SRC_DIR}/${SHADER_SRC}
            COMMENT "Preprocessing shader source ${SHADER_SRC_NAME_WE}.comp"
            VERBATIM
        )
        set_source_files_properties(${SHADER_COMP_HEADER} PROPERTIES GENERATED TRUE)
        list(APPEND RIFE_SHADER_SPV_HEX_FILES ${SHADER_COMP_HEADER})
    endmacro()

    rife_add_shader(rife_preproc.comp)
    rife_add_shader(rife_postproc.comp)
    rife_add_shader(rife_preproc_tta.comp)
    rife_add_shader(rife_postproc_tta.comp)
    rife_add_shader(rife_flow_tta_avg.comp)
    rife_add_shader(rife_v2_flow_tta_avg.comp)
    rife_add_shader(rife_v4_flow_tta_avg.comp)
    rife_add_shader(rife_flow_tta_temporal_avg.comp)
    rife_add_shader(rife_v2_flow_tta_temporal_avg.comp)
    rife_add_shader(rife_v4_flow_tta_temporal_avg.comp)
    rife_add_shader(rife_out_tta_temporal_avg.comp)
    rife_add_shader(rife_v4_timestep.comp)
    rife_add_shader(rife_v4_timestep_tta.comp)
    rife_add_shader(warp.comp)
    rife_add_shader(warp_pack4.comp)
    rife_add_shader(warp_pack8.comp)

    add_custom_target(rife_generate_spirv DEPENDS ${RIFE_SHADER_SPV_HEX_FILES})
else()
    message(WARNING
        "third_party/ncnn or third_party/rife-ncnn-vulkan/src/rife.cpp not "
        "found - building rife-engine in STUB mode. Run "
        "'git submodule update --init --recursive', then re-sync Gradle. "
        "See README_NATIVE_SETUP.md.")
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
    crash_handler.cpp
    $<$<BOOL:${RIFE_DEPS_AVAILABLE}>:${THIRD_PARTY_DIR}/rife-ncnn-vulkan/src/rife.cpp>
    $<$<BOOL:${RIFE_DEPS_AVAILABLE}>:${THIRD_PARTY_DIR}/rife-ncnn-vulkan/src/warp.cpp>
)

if(RIFE_DEPS_AVAILABLE)
    add_dependencies(rife_engine rife_generate_spirv)
endif()

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
    target_link_libraries(rife_engine PRIVATE ncnn vulkan)
else()
    target_compile_definitions(rife_engine PRIVATE RIFE_ENGINE_HAS_NCNN=0)
endif()

target_link_libraries(rife_engine PRIVATE
    android
    log
    mediandk
)

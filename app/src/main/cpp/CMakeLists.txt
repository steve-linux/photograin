# SPDX-License-Identifier: GPL-3.0-or-later
cmake_minimum_required(VERSION 3.22.1)
project(photograin_native C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# Everything ends up in shared libraries.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# Unoptimized, the image codecs take minutes per photo, so debug builds are optimized as well.
foreach(lang C CXX)
    set(CMAKE_${lang}_FLAGS_DEBUG "-O2 -DNDEBUG")
endforeach()

# LibRaw (LGPL-2.1 or CDDL-1.0), pinned as a git submodule. The *_ph.cpp files are
# placeholders that the upstream build leaves out too.
set(LIBRAW_DIR ${CMAKE_CURRENT_SOURCE_DIR}/LibRaw)
file(GLOB_RECURSE LIBRAW_SOURCES ${LIBRAW_DIR}/src/*.cpp)
list(FILTER LIBRAW_SOURCES EXCLUDE REGEX ".*_ph\\.cpp$")

add_library(raw STATIC ${LIBRAW_SOURCES})
target_include_directories(raw PUBLIC ${LIBRAW_DIR})
# Built thread-safe (no LIBRAW_NOTHREADS): thumbnails decode in parallel.
target_compile_definitions(raw PRIVATE LIBRAW_NODLL)
target_compile_options(raw PRIVATE -w -O2 -fexceptions)

# Portable core: plain C++ with no Android or NDK dependency, so it can be reused elsewhere.
add_library(raw_core STATIC raw_decoder.cpp)
target_link_libraries(raw_core PUBLIC raw)
target_compile_options(raw_core PRIVATE -Wall -O2 -fexceptions)

if(ANDROID)
    add_library(photograin_raw SHARED raw_jni.cpp)
    target_link_libraries(photograin_raw PRIVATE raw_core jnigraphics log)
    target_compile_options(photograin_raw PRIVATE -Wall -fexceptions)
    target_link_options(photograin_raw PRIVATE "-Wl,-z,max-page-size=16384")
endif()

# --- JPEG XL and AVIF ----------------------------------------------------------------------
# libjxl (BSD-3-Clause) with its highway, brotli and skcms submodules; libavif (BSD-2-Clause)
# with libaom (BSD-2-Clause) as the AV1 encoder. All are git submodules built from source,
# static, without their tools, tests or network downloads.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)

foreach(opt TOOLS DOXYGEN MANPAGES BENCHMARK EXAMPLES JNI SJPEG OPENEXR TRANSCODE_JPEG JPEGLI
            JPEGLI_LIBJPEG VIEWERS PLUGINS FUZZERS DEVTOOLS TCMALLOC)
    set(JPEGXL_ENABLE_${opt} OFF CACHE BOOL "" FORCE)
endforeach()
set(JPEGXL_ENABLE_SKCMS ON CACHE BOOL "" FORCE)
set(JPEGXL_ENABLE_BOXES ON CACHE BOOL "" FORCE)
set(JPEGXL_BUNDLE_LIBPNG OFF CACHE BOOL "" FORCE)
add_subdirectory(libjxl EXCLUDE_FROM_ALL)

foreach(opt APPS DOCS EXAMPLES TESTS TESTDATA TOOLS)
    set(ENABLE_${opt} OFF CACHE BOOL "" FORCE)
endforeach()
set(CONFIG_AV1_DECODER 0 CACHE STRING "" FORCE)
set(CONFIG_AV1_HIGHBITDEPTH 0 CACHE STRING "" FORCE)
set(CONFIG_LIBYUV 0 CACHE STRING "" FORCE)
set(CONFIG_WEBM_IO 0 CACHE STRING "" FORCE)
set(CONFIG_PIC 1 CACHE STRING "" FORCE)
if(CMAKE_SYSTEM_PROCESSOR MATCHES "x86|amd64|AMD64" AND ANDROID)
    # Emulators only: plain C avoids needing nasm on the build machine.
    set(AOM_TARGET_CPU generic CACHE STRING "" FORCE)
elseif(ANDROID)
    # libaom assembles its Arm code with a bare "as", which F-Droid's build server does not have;
    # the NDK's clang assembles it instead.
    set(CMAKE_ASM_COMPILER ${CMAKE_C_COMPILER} CACHE FILEPATH "" FORCE)
    set(AS_EXECUTABLE ${CMAKE_C_COMPILER} CACHE FILEPATH "" FORCE)
endif()
add_subdirectory(aom EXCLUDE_FROM_ALL)
target_include_directories(aom INTERFACE $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/aom>)

set(AVIF_CODEC_AOM SYSTEM CACHE STRING "" FORCE)  # "system" here means the aom target above
set(AVIF_CODEC_AOM_DECODE OFF CACHE BOOL "" FORCE)
set(AVIF_CODEC_AOM_ENCODE ON CACHE BOOL "" FORCE)
set(AVIF_LIBYUV OFF CACHE STRING "" FORCE)
set(AVIF_LIBSHARPYUV OFF CACHE STRING "" FORCE)
set(AVIF_ZLIBPNG OFF CACHE STRING "" FORCE)
set(AVIF_JPEG OFF CACHE STRING "" FORCE)
set(AVIF_BUILD_APPS OFF CACHE BOOL "" FORCE)
set(AVIF_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory(libavif EXCLUDE_FROM_ALL)

# Portable core, like raw_core.
add_library(codec_core STATIC image_codec.cpp)
target_link_libraries(codec_core PUBLIC jxl jxl_threads jxl_cms avif)
target_compile_options(codec_core PRIVATE -Wall -O2)

if(ANDROID)
    add_library(photograin_codec SHARED codec_jni.cpp)
    target_link_libraries(photograin_codec PRIVATE codec_core jnigraphics log)
    target_compile_options(photograin_codec PRIVATE -Wall)
    target_link_options(photograin_codec PRIVATE "-Wl,-z,max-page-size=16384")
endif()

# --- AI upscaling ----------------------------------------------------------------------------
# ncnn (BSD-3-Clause), pinned as a git submodule, runs Real-ESRGAN on the GPU through Vulkan
# (its glslang submodule compiles the shaders on the device) or on the CPU. Only the layers
# the bundled model and ncnn's own convolution paths use are built.
set(NCNN_LAYERS Input Convolution PReLU PixelShuffle Interp BinaryOp Split
                Packing Cast Padding Crop Flatten Reshape Gemm InnerProduct ReLU Clip Sigmoid Swish HardSwish
                HardSigmoid Mish ELU Eltwise Concat)
file(STRINGS ncnn/src/CMakeLists.txt NCNN_LAYER_LINES REGEX "^ncnn_add_layer\\(")
foreach(line ${NCNN_LAYER_LINES})
    string(REGEX REPLACE "^ncnn_add_layer\\(([A-Za-z0-9_]+).*" "\\1" layer "${line}")
    string(TOLOWER ${layer} name)
    if(layer IN_LIST NCNN_LAYERS)
        set(WITH_LAYER_${name} ON CACHE BOOL "" FORCE)
    else()
        set(WITH_LAYER_${name} OFF CACHE BOOL "" FORCE)
    endif()
endforeach()
foreach(opt BUILD_TOOLS BUILD_EXAMPLES BUILD_BENCHMARK BUILD_TESTS INSTALL_SDK C_API INT8 BF16
            PIXEL_ROTATE PIXEL_AFFINE PIXEL_DRAWING PYTHON SHARED_LIB)
    set(NCNN_${opt} OFF CACHE BOOL "" FORCE)
endforeach()
set(NCNN_SIMPLEOMP ON CACHE BOOL "" FORCE)  # ncnn's own OpenMP runtime: no libomp to ship
set(NCNN_VERSION 20260526 CACHE STRING "" FORCE)  # the pinned tag; the default is the build date
if(ANDROID)
    set(NCNN_VULKAN ON CACHE BOOL "" FORCE)
endif()
if(ANDROID AND NOT TARGET pthread)
    # ncnn links "pthread", which Android has inside libc: an empty target stands in for it.
    add_library(pthread INTERFACE)
endif()
add_subdirectory(ncnn EXCLUDE_FROM_ALL)

# Portable core, like raw_core.
add_library(upscale_core STATIC upscaler.cpp)
target_link_libraries(upscale_core PUBLIC ncnn)
target_compile_options(upscale_core PRIVATE -Wall -O2)

if(ANDROID)
    add_library(photograin_upscale SHARED upscale_jni.cpp)
    target_link_libraries(photograin_upscale PRIVATE upscale_core android jnigraphics log)
    target_compile_options(photograin_upscale PRIVATE -Wall)
    target_link_options(photograin_upscale PRIVATE "-Wl,-z,max-page-size=16384")
endif()

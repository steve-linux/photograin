# SPDX-License-Identifier: GPL-3.0-or-later
cmake_minimum_required(VERSION 3.22.1)
project(photograin_raw CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

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

cmake_minimum_required(VERSION 3.22.1)
project(opus_jni C CXX)

# ==============================================================
# libopus (vendored, built as static library)
# ==============================================================

# Disable Opus components we don't need
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/opus-1.5.2)

# ==============================================================
# JNI bridge shared library
# ==============================================================

add_library(opus_jni SHARED opus_jni.cpp)

target_include_directories(opus_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/opus-1.5.2/include
)

target_link_libraries(opus_jni PRIVATE
    opus
    log
)

# Strip symbols in release for smaller .so
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(opus_jni PRIVATE -s)
endif()

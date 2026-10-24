cmake_minimum_required(VERSION 3.22.1)
project("voxsherpa")

# We want to build the sherpa-onnx JNI wrapper.
set(SHERPA_ONNX_ENABLE_JNI ON CACHE BOOL "Enable JNI" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared libs" FORCE)

add_subdirectory(oboe)
add_subdirectory(sherpa-onnx)

add_library(
        voxsherpa
        SHARED
        native-lib.cpp)

target_link_libraries(
        voxsherpa
        oboe
        sherpa-onnx-c-api
        sherpa-onnx-jni
        log)

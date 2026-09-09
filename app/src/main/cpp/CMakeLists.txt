cmake_minimum_required(VERSION 3.22.1)
project("garment_jni")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 仅编译 arm64-v8a (RK3588)
if(ANDROID_ABI)
    message(STATUS "Building for ABI: ${ANDROID_ABI}")
endif()

add_library(
    garment_jni
    SHARED
    garment_jni.cpp
)

# 链接 NDK 的 log 和 jni
target_link_libraries(
    garment_jni
    log
    android
)

# 如果 obsensor_jni 提供了 C++ 公共头文件，可以添加：
# target_include_directories(garment_jni PRIVATE
#     ${CMAKE_SOURCE_DIR}/../obsensor_jni/src/main/cpp
# )
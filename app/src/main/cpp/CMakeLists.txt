cmake_minimum_required(VERSION 3.22.1)
project(thor_ndi)

set(NDI_SDK_DIR "${CMAKE_SOURCE_DIR}/../../../../third_party/ndi/NDI SDK for Android")

add_library(ndi SHARED IMPORTED)
set_target_properties(ndi PROPERTIES
    IMPORTED_LOCATION "${NDI_SDK_DIR}/lib/${ANDROID_ABI}/libndi.so"
)

add_library(thor_ndi SHARED ndi_jni.cpp)

target_include_directories(thor_ndi PRIVATE "${NDI_SDK_DIR}/include")

find_library(log-lib log)

target_link_libraries(thor_ndi
    ndi
    ${log-lib}
)

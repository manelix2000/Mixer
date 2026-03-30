cmake_minimum_required(VERSION 3.22.1)
project(mixer_aubio_jni LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(mixer_aubio_jni SHARED aubio_tempo_jni.cpp)

find_library(android_log_lib log)
target_link_libraries(mixer_aubio_jni PRIVATE ${android_log_lib})

set(MIXER_ANDROID_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../..")
set(AUBIO_PREBUILT_LIB "${MIXER_ANDROID_ROOT}/third_party/aubio/prebuilt/${ANDROID_ABI}/libaubio.a")

if(EXISTS "${AUBIO_PREBUILT_LIB}")
    add_library(aubio_static STATIC IMPORTED)
    set_target_properties(aubio_static PROPERTIES IMPORTED_LOCATION "${AUBIO_PREBUILT_LIB}")
    target_compile_definitions(mixer_aubio_jni PRIVATE MIXER_AUBIO_ENABLED=1)
    target_link_libraries(mixer_aubio_jni PRIVATE aubio_static)
else()
    target_compile_definitions(mixer_aubio_jni PRIVATE MIXER_AUBIO_ENABLED=0)
endif()

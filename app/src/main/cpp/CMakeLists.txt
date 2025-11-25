cmake_minimum_required(VERSION 3.22.1)

project(conguard_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Disable x86 AVX/FMA so whisper builds cleanly for Android
add_definitions(
        -DWHISPER_NO_AVX2=1
        -DWHISPER_NO_F16C=1
        -DWHISPER_NO_FMA=1
)

# 1) Bring in upstream whisper.cpp as subproject
#    Folder layout:
#    app/src/main/cpp/
#       CMakeLists.txt  (this file)
#       whisper_jni.cpp
#       whisper.cpp/    (upstream repo)
add_subdirectory(whisper.cpp)

# 2) Our JNI bridge library
add_library(
        conguard_jni
        SHARED
        whisper_jni.cpp
)

# 3) Include paths
target_include_directories(
        conguard_jni
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp
)

# 4) Android log lib
find_library(log-lib log)

# 5) Link against Android log + whisper target
target_link_libraries(
        conguard_jni
        whisper
        ggml
        ggml-base
        ggml-cpu
        android
        log
)

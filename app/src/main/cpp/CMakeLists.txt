cmake_minimum_required(VERSION 3.22)
project("llamabridge")

# llama.cpp sources are expected at this relative path.
# The actual llama.cpp tree must be set up per the llama-android reference project.
# See: https://github.com/ggml-org/llama.cpp/tree/master/examples/llama-android
set(LLAMA_CPP_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Conditionally add llama.cpp static lib only if the source exists.
# If you haven't cloned llama.cpp yet, the build will skip this target
# and AION will use cloud-only mode until the native library is available.
if(EXISTS ${LLAMA_CPP_SRC_DIR}/llama.h)
    add_library(llama STATIC
        ${LLAMA_CPP_SRC_DIR}/llama.cpp
        ${LLAMA_CPP_SRC_DIR}/ggml.c
        ${LLAMA_CPP_SRC_DIR}/ggml-alloc.c
        ${LLAMA_CPP_SRC_DIR}/ggml-backend.c
        ${LLAMA_CPP_SRC_DIR}/ggml-quants.c
        ${LLAMA_CPP_SRC_DIR}/ggml-cpu.c
        ${LLAMA_CPP_SRC_DIR}/ggml-cpu/ggml-cpu-aarch64.cpp
    )

    add_library(llamabridge SHARED llamabridge.cpp)

    target_include_directories(llamabridge PRIVATE
        ${LLAMA_CPP_SRC_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}
    )

    target_link_libraries(llamabridge PRIVATE llama log dl)

    # Android ABI restriction
    if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
        target_compile_options(llabridge PRIVATE -march=armv8-a+fp+simd)
    endif()
else()
    # Stub target — llama.cpp source not available. Build a no-op library
    # so the Gradle build doesn't fail. AION will degrade gracefully.
    add_library(llamabridge SHARED llamabridge_stub.cpp)
    target_link_libraries(llamabridge PRIVATE log)
endif()

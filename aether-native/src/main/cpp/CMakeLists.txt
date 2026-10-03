cmake_minimum_required(VERSION 3.22.1)
project(aether)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ABI: arm64-v8a only as per spec
# Build flags
add_compile_options(-Wall -Wextra -Werror=return-type -fexceptions -frtti)

add_library(aether SHARED
    jni/jni_onload.cpp
    jni/native_registry.cpp
    bridge/engine_bridge.cpp
    bridge/message_bridge.cpp
    runtime/runtime_state.cpp
    platform/thread_dispatcher.cpp
)

target_include_directories(aether PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(aether
    ${log-lib}
    ${android-lib}
)

# Optional: strip debug symbols in release
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET aether POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:aether>
        COMMENT "Stripping libaether.so"
    )
endif()

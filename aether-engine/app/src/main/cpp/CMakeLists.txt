cmake_minimum_required(VERSION 3.22.1)
project(aether LANGUAGES CXX)

add_library(aether SHARED
    jni_bridge.cpp
    runtime_init.cpp
)

target_compile_options(aether PRIVATE -std=c++17 -O2 -fvisibility=hidden)
target_link_libraries(aether PRIVATE log)

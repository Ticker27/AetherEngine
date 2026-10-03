cmake_minimum_required(VERSION 3.22)

project(AetherEngineNative
        VERSION 0.1.0
        LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

enable_testing()

add_library(aether-core STATIC
    aether-core/aether_core.cpp
)

target_include_directories(aether-core
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/aether-core
)

add_executable(aether-core-test
    aether-core/aether_core_test.cpp
)

target_link_libraries(aether-core-test
    PRIVATE
        aether-core
)

add_test(
    NAME aether-core-test
    COMMAND aether-core-test
)

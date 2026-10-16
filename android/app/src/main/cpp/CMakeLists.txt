project(aether CXX)
cmake_minimum_required(VERSION 3.22)

add_library(aether SHARED aether.cpp)
target_compile_features(aether PRIVATE cxx_std_17)
target_compile_options(aether PRIVATE -Wall -Wextra)
find_library(log-lib log)
target_link_libraries(aether ${log-lib})

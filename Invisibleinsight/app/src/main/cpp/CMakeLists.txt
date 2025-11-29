cmake_minimum_required(VERSION 3.22.1)
project("invisibleinsight-native")

# Aggressively disable llama.cpp dependencies that are not available on Android
# FORCE is required to override cached values
set(LLAMA_CURL OFF CACHE BOOL "Disable CURL" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable Tests" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable Examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Disable Server" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build Common" FORCE)

# Disable OpenMP to avoid libomp.so dependency issues
set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP" FORCE)

# Use llama.cpp's own CMake setup
add_subdirectory(llama.cpp)

add_library(native-lib SHARED native-lib.cpp)

# Link against llama (the library target from the submodule) and Android logs
target_link_libraries(native-lib llama common android log)
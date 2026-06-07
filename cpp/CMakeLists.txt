cmake_minimum_required(VERSION 3.25)
project(cross-asset-ems CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler hardening flags
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    add_compile_options(-Wall -Wextra -Wpedantic -Werror)
endif()

# Modules — same layering as the Java tree. Stubs populate when task 1.7 codegen lands.
add_subdirectory(ems-core)
add_subdirectory(ems-fsm)
add_subdirectory(ems-transport)
add_subdirectory(ems-aaa)
add_subdirectory(ems-validator)
add_subdirectory(ems-oms)
add_subdirectory(ems-fix-bridge)
add_subdirectory(ems-market-data)
add_subdirectory(ems-pretrade)
add_subdirectory(ems-venue-connectivity)
add_subdirectory(ems-posttrade)
add_subdirectory(ems-observability)
add_subdirectory(ems-ops)
add_subdirectory(ems-bench)
add_subdirectory(ems-it)

enable_testing()

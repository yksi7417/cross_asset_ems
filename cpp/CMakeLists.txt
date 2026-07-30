cmake_minimum_required(VERSION 3.25)
project(cross-asset-ems CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Default to an optimised-with-symbols build. Several hardening options below
# (notably _FORTIFY_SOURCE) are no-ops or warnings without optimisation, and an
# unspecified build type would silently drop them.
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE RelWithDebInfo CACHE STRING "Build type" FORCE)
endif()

# Warning set — see docs/decisions/0004-defensive-gate-stack.md §A.
# Memory corruption is the named risk of adding C++, so the warning set is the
# first line of the gate, not a nicety.
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    add_compile_options(
        -Wall -Wextra -Wpedantic -Werror
        -Wconversion -Wshadow -Wold-style-cast -Wnon-virtual-dtor -Wcast-qual
    )
    # -Wuseless-cast is GCC-only. -Werror makes an unknown warning flag fatal on
    # Clang, so it must be guarded rather than added unconditionally.
    if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        add_compile_options(-Wuseless-cast)
    endif()

    # Hardening. Cheap at runtime, and turns several classes of undefined
    # behaviour into a deterministic abort instead of silent corruption.
    add_compile_definitions(_GLIBCXX_ASSERTIONS)
    add_compile_options(-fstack-protector-strong)
    # _FORTIFY_SOURCE requires optimisation. At -O0 it emits a #warning, which
    # -Werror turns into a build failure — so it is applied only to optimised
    # configurations rather than unconditionally.
    if(CMAKE_BUILD_TYPE MATCHES "^(Release|RelWithDebInfo|MinSizeRel)$")
        add_compile_definitions(_FORTIFY_SOURCE=3)
    endif()
endif()

# Sanitizer configuration — driven by scripts/ci/gate.sh, which builds each
# sanitizer in its own directory (build/cpp-asan, build/cpp-tsan, ...).
#   -DEMS_SANITIZER=address-undefined | thread | memory | none
set(EMS_SANITIZER "none" CACHE STRING "Sanitizer build configuration")
set_property(CACHE EMS_SANITIZER PROPERTY STRINGS none address-undefined thread memory)

if(NOT EMS_SANITIZER STREQUAL "none")
    if(EMS_SANITIZER STREQUAL "address-undefined")
        set(EMS_SANITIZER_FLAGS -fsanitize=address,undefined -fno-sanitize-recover=all)
    elseif(EMS_SANITIZER STREQUAL "thread")
        set(EMS_SANITIZER_FLAGS -fsanitize=thread)
    elseif(EMS_SANITIZER STREQUAL "memory")
        set(EMS_SANITIZER_FLAGS -fsanitize=memory -fsanitize-memory-track-origins)
    else()
        message(FATAL_ERROR "Unknown EMS_SANITIZER: ${EMS_SANITIZER}")
    endif()
    # ASan/MSan need frame pointers and line info to produce a usable report.
    add_compile_options(${EMS_SANITIZER_FLAGS} -fno-omit-frame-pointer -g)
    add_link_options(${EMS_SANITIZER_FLAGS})
    message(STATUS "EMS sanitizer build: ${EMS_SANITIZER}")
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

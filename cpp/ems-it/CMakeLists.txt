# ems-it — cross-module integration surface, and the home of the `ems-slice`
# binary the polyglot conformance gate runs.
#
# The binary lands at build/cpp/ems-it/ems-slice, which is the path
# conformance/harness/run.sh looks for. See conformance/README.md.

add_library(ems-it
    src/slice_runner.cpp
)
target_include_directories(ems-it PUBLIC include/ ${CMAKE_CURRENT_SOURCE_DIR}/../fsm/generated)
target_compile_features(ems-it PUBLIC cxx_std_20)
target_link_libraries(ems-it PUBLIC ems-core ems-fsm ems-transport ems-aaa ems-validator)

add_executable(ems-slice src/slice_main.cpp)
target_link_libraries(ems-slice PRIVATE ems-it ems-transport)

ems_add_test(ems-it-slice-runner-test SOURCES test/slice_runner_test.cpp LIBS ems-it)

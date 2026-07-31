add_library(ems-fsm INTERFACE)
target_include_directories(ems-fsm INTERFACE
  include/
  ${CMAKE_CURRENT_SOURCE_DIR}/../fsm/
)
target_compile_features(ems-fsm INTERFACE cxx_std_20)
target_link_libraries(ems-fsm INTERFACE ems-core)

# Compile-verify: one TU that pulls in all five generated FSM headers.
# This catches type errors and ODR violations across the full header set.
add_executable(ems-fsm-compile-test test/fsm_compile_test.cpp)
target_link_libraries(ems-fsm-compile-test PRIVATE ems-fsm)
enable_testing()
add_test(NAME ems-fsm-compile-test COMMAND ems-fsm-compile-test)

# Behavioural coverage of the generated effect tables. The compile-verify above
# proves the headers parse; this proves they answer correctly.
ems_add_test(ems-fsm-effects-test SOURCES test/fsm_effects_test.cpp LIBS ems-fsm)

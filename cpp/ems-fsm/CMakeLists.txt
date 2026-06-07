add_library(ems-fsm INTERFACE)
target_include_directories(ems-fsm INTERFACE include/)
target_compile_features(ems-fsm INTERFACE cxx_std_20)
target_link_libraries(ems-fsm INTERFACE ems-core)

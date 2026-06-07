add_library(ems-oms INTERFACE)
target_include_directories(ems-oms INTERFACE include/)
target_compile_features(ems-oms INTERFACE cxx_std_20)
target_link_libraries(ems-oms INTERFACE ems-core ems-fsm)

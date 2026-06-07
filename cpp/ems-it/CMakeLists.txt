add_library(ems-it INTERFACE)
target_include_directories(ems-it INTERFACE include/)
target_compile_features(ems-it INTERFACE cxx_std_20)
target_link_libraries(ems-it INTERFACE ems-core ems-fsm ems-transport)

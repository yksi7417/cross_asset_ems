add_library(ems-validator INTERFACE)
target_include_directories(ems-validator INTERFACE include/)
target_compile_features(ems-validator INTERFACE cxx_std_20)
target_link_libraries(ems-validator INTERFACE ems-core)

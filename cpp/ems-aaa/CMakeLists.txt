add_library(ems-aaa INTERFACE)
target_include_directories(ems-aaa INTERFACE include/)
target_compile_features(ems-aaa INTERFACE cxx_std_20)
target_link_libraries(ems-aaa INTERFACE ems-core)

add_library(ems-bench INTERFACE)
target_include_directories(ems-bench INTERFACE include/)
target_compile_features(ems-bench INTERFACE cxx_std_20)
target_link_libraries(ems-bench INTERFACE ems-core)

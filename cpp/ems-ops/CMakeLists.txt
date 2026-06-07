add_library(ems-ops INTERFACE)
target_include_directories(ems-ops INTERFACE include/)
target_compile_features(ems-ops INTERFACE cxx_std_20)
target_link_libraries(ems-ops INTERFACE ems-core)

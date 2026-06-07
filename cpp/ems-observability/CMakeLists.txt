add_library(ems-observability INTERFACE)
target_include_directories(ems-observability INTERFACE include/)
target_compile_features(ems-observability INTERFACE cxx_std_20)
target_link_libraries(ems-observability INTERFACE ems-core)

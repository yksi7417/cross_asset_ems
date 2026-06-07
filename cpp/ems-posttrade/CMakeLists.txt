add_library(ems-posttrade INTERFACE)
target_include_directories(ems-posttrade INTERFACE include/)
target_compile_features(ems-posttrade INTERFACE cxx_std_20)
target_link_libraries(ems-posttrade INTERFACE ems-core)

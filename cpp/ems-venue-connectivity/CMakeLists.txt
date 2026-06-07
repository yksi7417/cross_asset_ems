add_library(ems-venue-connectivity INTERFACE)
target_include_directories(ems-venue-connectivity INTERFACE include/)
target_compile_features(ems-venue-connectivity INTERFACE cxx_std_20)
target_link_libraries(ems-venue-connectivity INTERFACE ems-core ems-transport)

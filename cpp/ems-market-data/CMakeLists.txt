add_library(ems-market-data INTERFACE)
target_include_directories(ems-market-data INTERFACE include/)
target_compile_features(ems-market-data INTERFACE cxx_std_20)
target_link_libraries(ems-market-data INTERFACE ems-core ems-transport)

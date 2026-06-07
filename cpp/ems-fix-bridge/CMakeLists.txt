add_library(ems-fix-bridge INTERFACE)
target_include_directories(ems-fix-bridge INTERFACE include/)
target_compile_features(ems-fix-bridge INTERFACE cxx_std_20)
target_link_libraries(ems-fix-bridge INTERFACE ems-core ems-transport)

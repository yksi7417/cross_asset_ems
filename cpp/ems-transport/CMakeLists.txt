add_library(ems-transport INTERFACE)
target_include_directories(ems-transport INTERFACE include/)
target_compile_features(ems-transport INTERFACE cxx_std_20)
target_link_libraries(ems-transport INTERFACE ems-core)

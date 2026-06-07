add_library(ems-pretrade INTERFACE)
target_include_directories(ems-pretrade INTERFACE include/)
target_compile_features(ems-pretrade INTERFACE cxx_std_20)
target_link_libraries(ems-pretrade INTERFACE ems-core)

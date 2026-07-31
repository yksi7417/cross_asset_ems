# ems-aaa — session lookup and the entitlement decision.
#
# Authorization decision only; no logon, SSO or SCIM (ADR 0002). Mirrors
# java/ems-aaa .../aaa/slice and rust/ems-aaa.

add_library(ems-aaa
    src/aaa.cpp
)
target_include_directories(ems-aaa PUBLIC include/)
target_compile_features(ems-aaa PUBLIC cxx_std_20)
target_link_libraries(ems-aaa PUBLIC ems-core)

ems_add_test(ems-aaa-test SOURCES test/aaa_test.cpp LIBS ems-aaa)

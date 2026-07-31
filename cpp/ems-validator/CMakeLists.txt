# ems-validator — the layered validation pipeline.
#
# Ports io.crossasset.ems.validator.LayeredValidatorPipeline; mirrors
# rust/ems-validator. Layer order is part of the contract.

add_library(ems-validator
    src/validator.cpp
)
target_include_directories(ems-validator PUBLIC include/)
target_compile_features(ems-validator PUBLIC cxx_std_20)
target_link_libraries(ems-validator PUBLIC ems-core ems-aaa)

ems_add_test(ems-validator-test SOURCES test/validator_test.cpp LIBS ems-validator)

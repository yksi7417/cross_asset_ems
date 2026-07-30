# ems-core — event journal codec and deterministic identifiers.
#
# The byte layout journal.cpp produces is a contract with java/ems-core and
# rust/ems-core; the conformance harness diffs all three. See
# conformance/README.md.

add_library(ems-core
    src/journal.cpp
    src/ids.cpp
)
target_include_directories(ems-core PUBLIC include/)
target_compile_features(ems-core PUBLIC cxx_std_20)

ems_add_test(ems-core-journal-test SOURCES test/journal_test.cpp LIBS ems-core)
ems_add_test(ems-core-ids-test SOURCES test/ids_test.cpp LIBS ems-core)

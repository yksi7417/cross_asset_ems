# ems-transport — the transport seam.
#
# JournalTransport is the deterministic file-backed implementation the
# conformance gate runs; an Aeron-backed one is the live path.
# See docs/decisions/0006-abstract-transport-journal-first.md.

add_library(ems-transport
    src/journal_transport.cpp
)
target_include_directories(ems-transport PUBLIC include/)
target_compile_features(ems-transport PUBLIC cxx_std_20)
target_link_libraries(ems-transport PUBLIC ems-core)

ems_add_test(ems-transport-journal-test
    SOURCES test/journal_transport_test.cpp
    LIBS ems-transport)

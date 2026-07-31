#!/usr/bin/env python3
"""Golden hashes of the generated FSM sources, pinned before the Rust emitter landed.

``tools/codegen/fsm_codegen.py`` is 1,300 lines and had no tests. Adding a third
emitter to it is exactly the change that can perturb the other two by accident —
a shared helper tweaked, a dict iterated in a new order, a trailing newline
moved. The Java and C++ trees are compiled and tested, so a *semantic* break
would surface; a cosmetic one would not, and cosmetic is enough to fail the
``fsm-sync`` gate step on someone else's machine.

These hashes are a characterisation test, not an aspiration: they record what
the generator produced on the commit before the Rust work started.

**When the generated output legitimately changes** — a schema edit, a fix to an
emitter — regenerate, review the diff, and update the hash. That is a visible,
reviewable edit rather than a silent drift.

The C++ hashes were updated once, when `name()` / `FsmEventFromName` were added
to the C++ emitter so state and event names could reach the journal. The diff
was pure insertion: no existing line changed, which is what made the update
safe to make rather than a signal that something had broken.

They were updated a second time when effects landed in the Rust and C++
emitters. That diff was *not* pure insertion — every return site in every
generated `transition` gained a field — so it was reviewed differently: the
removed lines were filtered down to confirm they were all `return {...}` sites
plus the obsolete "effects: deferred" comment, and nothing behavioural moved.
Rust hashes were added at the same time. The Rust tree had been generated for
several commits with no pin at all, which meant the emitter this test exists to
protect was the one emitter it did not cover.

Run: python3 -m unittest discover -s tools/codegen -p 'test_*.py'
"""

from __future__ import annotations

import hashlib
import pathlib
import unittest

JAVA_GENERATED = pathlib.Path("java/ems-fsm/src/main/generated")
CPP_GENERATED = pathlib.Path("cpp/fsm/generated")
RUST_GENERATED = pathlib.Path("rust/ems-fsm/src/generated")

JAVA_GOLDEN = {
    "io/crossasset/ems/fsm/generated/MultiLegFsmContext.java": "44e30180f7b785a055fff8aff463385721a70062d7fff55e5f0f5701ef804cf7",
    "io/crossasset/ems/fsm/generated/MultiLegFsmEffect.java": "fc7a73a2548ce80a2f97a75ff8788d4c2aa581f095204e99e912197a3995288f",
    "io/crossasset/ems/fsm/generated/MultiLegFsmEvent.java": "647a7b3b81405d9635c3635ca38d1f8422c9d957f64676ad19e343a2ff171a7c",
    "io/crossasset/ems/fsm/generated/MultiLegFsmPayloads.java": "e63427bc230c711cc35a4df9afde01b5875232664ddcd383c0be488810660434",
    "io/crossasset/ems/fsm/generated/MultiLegFsmRunner.java": "770503bd80492c15618bf27128b82d8ce79d184510427bb1fe9b1016966abfc4",
    "io/crossasset/ems/fsm/generated/MultiLegFsmState.java": "01225ac0c3b8e766d4c361b47d3046befaff247e77d63b0402d7b6439bb95fdc",
    "io/crossasset/ems/fsm/generated/OrderFsmContext.java": "c2db93663f0c471b1ac6995db7da3176935625ca08f5eb6e523a771c52e430e7",
    "io/crossasset/ems/fsm/generated/OrderFsmEffect.java": "e5b91d297948c40567d1bd935ac4d818b43de1d98cbf02887007ec2ade016207",
    "io/crossasset/ems/fsm/generated/OrderFsmEvent.java": "0e4915ad4163a154c2997193669f7d38f9a603ae7de569858c97444c9ee1e4af",
    "io/crossasset/ems/fsm/generated/OrderFsmPayloads.java": "bb80aee6b3f309ec82fe357e8beadd0013e16f35b70f84ccdb0a2b6ee570d191",
    "io/crossasset/ems/fsm/generated/OrderFsmRunner.java": "fe0b4fba94cfa87b9779a1cce77ee0aecc579017774eaa380056827b1f669085",
    "io/crossasset/ems/fsm/generated/OrderFsmState.java": "1c1479319a99c9fa4c48301bcc1d30b7a4f770736c24801e8499615ef382ea44",
    "io/crossasset/ems/fsm/generated/RouteFsmContext.java": "c5c961f72ae3e8105e88289b863495b980438f804f34b1067244cd02eaec07f2",
    "io/crossasset/ems/fsm/generated/RouteFsmEffect.java": "8d4556096063054c9f904fb017b9b8235b3f6c0a66df343bbdc11119d72a2ae0",
    "io/crossasset/ems/fsm/generated/RouteFsmEvent.java": "e4730c814ff6d5658c26f64248b8174373307b564f29990cf2f0c9adc9368bad",
    "io/crossasset/ems/fsm/generated/RouteFsmPayloads.java": "6c79637668da1be69358585bf697d3c7f9e1c53e58328e0d80aaa3b309ec56ad",
    "io/crossasset/ems/fsm/generated/RouteFsmRunner.java": "1de5807e8b8b352ba6d97a9583aa10b4f5752d824da66224253b3d69f100d9ba",
    "io/crossasset/ems/fsm/generated/RouteFsmState.java": "4e4d38306e7f4d5e8d4adf34746113e21132f8da4fa52cf44ed2396633acb6b9",
    "io/crossasset/ems/fsm/generated/SorFsmContext.java": "7974e6b8226ac463292b1a7d636a27b0c3728f577d48dae47b768b5e95895bfe",
    "io/crossasset/ems/fsm/generated/SorFsmEffect.java": "34e2db37fea66affba91e584478a4f1efe18ac63cb1eb57d379646a128e24c28",
    "io/crossasset/ems/fsm/generated/SorFsmEvent.java": "13c771dfc0ca37fee102c5f9b455a6573e475816ea96e95d1c26e5223271dc50",
    "io/crossasset/ems/fsm/generated/SorFsmPayloads.java": "121ea1ea10463bf0f329b4d0026cd92bc197e0e926107ff8e551ff75ee5122b9",
    "io/crossasset/ems/fsm/generated/SorFsmRunner.java": "38dd6076e0a98966e220fd9e747f18f53d97006ad05c29b89c62e5c5d4bd5932",
    "io/crossasset/ems/fsm/generated/SorFsmState.java": "a56f05ba1bb4ae406a908c05b8268c3287ed63494013cc13c05d63cdb6e205d5",
    "io/crossasset/ems/fsm/generated/TransitionResult.java": "27af3b71964a42a0c0327c90035a8af80b98525d2936214268eeae97b14257b0",
    "io/crossasset/ems/fsm/generated/VenueSessionFsmContext.java": "793be70c82d9414813e7738b612a88410634b3a27eccbc4ece1b8db34d082d62",
    "io/crossasset/ems/fsm/generated/VenueSessionFsmEffect.java": "0526a26bce3536c6e51245850d5d4a55664be2f4188e0423157a7f04f88509b5",
    "io/crossasset/ems/fsm/generated/VenueSessionFsmEvent.java": "35091ea52edd0aa5091d5b37413746fcde16c98bb62d10bb4fa27a23b9b2d1b3",
    "io/crossasset/ems/fsm/generated/VenueSessionFsmRunner.java": "9a4e78c7b1116b74cc5e3b8656a1e7ccfc17c4a5b120b72ea7a3e69cd8c428a9",
    "io/crossasset/ems/fsm/generated/VenueSessionFsmState.java": "3fe11f348e2d24b8c331a45fd89039849a852fc21741bd38f613a50864c482d0",
    "org/jspecify/annotations/Nullable.java": "c5814db5c0f9b2137756f15745c7431c925dd0908478180931777e09bdaaf507",
}

CPP_GOLDEN = {
    "multileg_fsm.hpp": "74baa476fb65bea025108eed169fa4d6c25d481fe2d9a5fd720e81a5e0afa622",
    "order_fsm.hpp": "e44ca8c1394173398cf04212226646a10b88a3b85198b5693e1a995e9f6b869e",
    "route_fsm.hpp": "f21e2606ad4492c057742f0c103bc6ffe6413ea0299f523abfe25888404ab11b",
    "sor_fsm.hpp": "99956129bce2455f2b69fda70637510cb1fb34a99b61e9563225cffed1850136",
    "venuesession_fsm.hpp": "3a9debfae9854a2d6288cd4f088504c590dcfcec5cbaca553591fe8597017fa9",
}

RUST_GOLDEN = {
    "mod.rs": "716d96c5b64d038a92d7dd7a2c25e14db57279b740732d44951b431a5147d73a",
    "multi_leg_fsm.rs": "fc6513a4abd1230a4c4a6951b68aacd296428b90e4cc82a2cd10cae53826cf66",
    "order_fsm.rs": "86282d48c377e1bb5814f14a0a672a9e2bb2c28a2ef1bf8807ef4676070be593",
    "route_fsm.rs": "f05bc3e752046a6c123a7e8ccc3770e6abf594f4098da730ec2a51c9ca80a14f",
    "sor_fsm.rs": "674217c0844ac1b17ce9317248add42c3591198c7104179daa98f3b62249d179",
    "venue_session_fsm.rs": "4d99f0bb10dd1fc38f5a51dad89cb1493b6b9490cae749daf5b24e5c886307f0",
}


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class TestGeneratedOutputIsUnchanged(unittest.TestCase):
    """The Rust emitter must not perturb the Java or C++ output."""

    def test_java_file_set_is_unchanged(self):
        actual = {
            str(p.relative_to(JAVA_GENERATED))
            for p in JAVA_GENERATED.rglob("*.java")
        }
        self.assertEqual(actual, set(JAVA_GOLDEN), "generated Java file set changed")

    def test_java_file_contents_are_unchanged(self):
        for name, expected in sorted(JAVA_GOLDEN.items()):
            with self.subTest(file=name):
                self.assertEqual(sha256(JAVA_GENERATED / name), expected)

    def test_cpp_file_set_is_unchanged(self):
        actual = {p.name for p in CPP_GENERATED.glob("*.hpp")}
        self.assertEqual(actual, set(CPP_GOLDEN), "generated C++ file set changed")

    def test_cpp_file_contents_are_unchanged(self):
        for name, expected in sorted(CPP_GOLDEN.items()):
            with self.subTest(file=name):
                self.assertEqual(sha256(CPP_GENERATED / name), expected)

    def test_rust_file_set_is_unchanged(self):
        actual = {p.name for p in RUST_GENERATED.glob("*.rs")}
        self.assertEqual(actual, set(RUST_GOLDEN), "generated Rust file set changed")

    def test_rust_file_contents_are_unchanged(self):
        for name, expected in sorted(RUST_GOLDEN.items()):
            with self.subTest(file=name):
                self.assertEqual(sha256(RUST_GENERATED / name), expected)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Lifecycle chaining tests — task 1.9.

Verifies that emit_event cascade effects between FSMs are correctly wired:
every target_fsm is a known FSM and every cascaded event is declared in
that target FSM. Also asserts the specific canonical cascade paths
documented in arch-order-route-lifecycle and arch-fix-fsm-design.

Run:
    python3 -m pytest tools/fsm-validator/test_lifecycle_chaining.py -v
    # or from repo root with pytest installed
"""

from pathlib import Path
from typing import NamedTuple

import pytest
import yaml

REPO_ROOT = Path(__file__).parent.parent.parent
FSM_DIR = REPO_ROOT / "schemas" / "fsm"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def load_all_fsms() -> dict[str, dict]:
    """Return {fsm_name: doc} for every *.fsm.yaml in schemas/fsm/."""
    fsms: dict[str, dict] = {}
    for path in sorted(FSM_DIR.glob("*.fsm.yaml")):
        with open(path) as f:
            doc = yaml.safe_load(f)
        fsms[doc["name"]] = doc
    return fsms


def event_names(doc: dict) -> set[str]:
    return {e["name"] for e in doc.get("events", [])}


class CascadeEdge(NamedTuple):
    source_fsm: str
    from_state: str
    trigger_event: str
    target_fsm: str
    emitted_event: str


def collect_cascade_edges(fsms: dict[str, dict]) -> list[CascadeEdge]:
    """Enumerate every emit_event effect across all loaded FSMs."""
    edges: list[CascadeEdge] = []
    for fsm_name, doc in fsms.items():
        for t in doc.get("transitions", []):
            for effect in t.get("effects", []):
                if effect.get("kind") == "emit_event":
                    args = effect.get("args", {})
                    edges.append(
                        CascadeEdge(
                            source_fsm=fsm_name,
                            from_state=t["from"],
                            trigger_event=t["event"],
                            target_fsm=args.get("target_fsm", ""),
                            emitted_event=args.get("event", ""),
                        )
                    )
    return edges


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def fsms() -> dict[str, dict]:
    return load_all_fsms()


@pytest.fixture(scope="module")
def edges(fsms) -> list[CascadeEdge]:
    return collect_cascade_edges(fsms)


# ---------------------------------------------------------------------------
# Structural integrity: every target_fsm is known and every emitted event
# is declared in the target FSM.
# ---------------------------------------------------------------------------

class TestCascadeWiringIntegrity:
    def test_all_target_fsms_are_known(self, fsms, edges):
        unknown = {e.target_fsm for e in edges} - set(fsms)
        assert not unknown, f"emit_event targets unknown FSMs: {sorted(unknown)}"

    def test_all_emitted_events_declared_in_target_fsm(self, fsms, edges):
        failures = []
        for edge in edges:
            if edge.target_fsm not in fsms:
                continue  # caught by previous test
            declared = event_names(fsms[edge.target_fsm])
            if edge.emitted_event not in declared:
                failures.append(
                    f"{edge.source_fsm} emits '{edge.emitted_event}' → {edge.target_fsm} "
                    f"but that event is not declared in {edge.target_fsm}"
                )
        assert not failures, "\n".join(failures)


# ---------------------------------------------------------------------------
# Semantic: specific cascade paths required by arch-order-route-lifecycle
# and arch-fix-fsm-design.
# ---------------------------------------------------------------------------

class TestOrderRouteCascade:
    """Order cancel → Route cancel cascade (arch-fix-fsm-design § Lifecycle Chaining)."""

    def test_order_cancel_accepted_emits_route_cancel(self, edges):
        # When OrderFsm.PENDING_CANCEL transitions on CancelAccepted → CANCELED,
        # it must emit RouteCancelRequested to RouteFsm.
        matching = [
            e for e in edges
            if e.source_fsm == "OrderFsm"
            and e.trigger_event == "CancelAccepted"
            and e.target_fsm == "RouteFsm"
            and e.emitted_event == "RouteCancelRequested"
        ]
        assert matching, (
            "OrderFsm CancelAccepted must emit RouteCancelRequested → RouteFsm "
            "(cascade cancel required by arch-fix-fsm-design § Lifecycle Chaining)"
        )


class TestRouteFillCascade:
    """Route fill → Order fill cascade."""

    def test_route_partially_filled_emits_order_partial_fill(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "RouteFsm"
            and e.trigger_event == "RoutePartiallyFilled"
            and e.target_fsm == "OrderFsm"
            and e.emitted_event == "PartialFill"
        ]
        assert matching, (
            "RouteFsm RoutePartiallyFilled must emit PartialFill → OrderFsm"
        )

    def test_route_filled_emits_order_full_fill(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "RouteFsm"
            and e.trigger_event == "RouteFilled"
            and e.target_fsm == "OrderFsm"
            and e.emitted_event == "FullFill"
        ]
        assert matching, (
            "RouteFsm RouteFilled must emit FullFill → OrderFsm"
        )


class TestRouteAckCascade:
    """Route acknowledgment → Order state cascade."""

    def test_route_acknowledged_emits_order_validation_passed(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "RouteFsm"
            and e.trigger_event == "RouteAcknowledged"
            and e.target_fsm == "OrderFsm"
            and e.emitted_event == "ValidationPassed"
        ]
        assert matching, (
            "RouteFsm RouteAcknowledged must emit ValidationPassed → OrderFsm"
        )

    def test_route_rejected_emits_order_validation_failed(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "RouteFsm"
            and e.trigger_event == "RouteRejected"
            and e.target_fsm == "OrderFsm"
            and e.emitted_event == "ValidationFailed"
        ]
        assert matching, (
            "RouteFsm RouteRejected must emit ValidationFailed → OrderFsm"
        )


class TestRouteCancelRejectCascade:
    """Route cancel-reject → Order cancel-reject cascade."""

    def test_route_cancel_rejected_emits_order_cancel_rejected(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "RouteFsm"
            and e.trigger_event == "RouteCancelRejected"
            and e.target_fsm == "OrderFsm"
            and e.emitted_event == "CancelRejected"
        ]
        assert matching, (
            "RouteFsm RouteCancelRejected must emit CancelRejected → OrderFsm"
        )


class TestRouteReplaceCascade:
    """Route replace outcome → Order replace cascade."""

    def test_route_replaced_emits_order_replace_accepted(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "RouteFsm"
            and e.trigger_event == "RouteReplaced"
            and e.target_fsm == "OrderFsm"
            and e.emitted_event == "ReplaceAccepted"
        ]
        assert matching, (
            "RouteFsm RouteReplaced must emit ReplaceAccepted → OrderFsm"
        )

    def test_route_replace_rejected_emits_order_replace_rejected(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "RouteFsm"
            and e.trigger_event == "RouteReplaceRejected"
            and e.target_fsm == "OrderFsm"
            and e.emitted_event == "ReplaceRejected"
        ]
        assert matching, (
            "RouteFsm RouteReplaceRejected must emit ReplaceRejected → OrderFsm"
        )


class TestSorCascade:
    """SOR FSM must mirror RouteFsm cascade wiring to OrderFsm."""

    def test_sor_fill_cascades_to_order(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "SorFsm"
            and e.trigger_event == "RouteFilled"
            and e.target_fsm == "OrderFsm"
            and e.emitted_event == "FullFill"
        ]
        assert matching, "SorFsm RouteFilled must emit FullFill → OrderFsm"

    def test_sor_reject_cascades_to_order(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "SorFsm"
            and e.trigger_event == "RouteRejected"
            and e.target_fsm == "OrderFsm"
            and e.emitted_event == "ValidationFailed"
        ]
        assert matching, "SorFsm RouteRejected must emit ValidationFailed → OrderFsm"


class TestMultiLegCancelCascade:
    """MultiLeg ALL_OR_NONE failure must cascade cancel to RouteFsm."""

    def test_multileg_aon_reject_cascades_route_cancel(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "MultiLegFsm"
            and e.trigger_event == "LegRejected"
            and e.target_fsm == "RouteFsm"
            and e.emitted_event == "RouteCancelRequested"
        ]
        assert matching, (
            "MultiLegFsm LegRejected (ALL_OR_NONE guard) must emit "
            "RouteCancelRequested → RouteFsm to cascade cancel remaining legs"
        )

    def test_multileg_cancel_requested_cascades_route_cancel(self, edges):
        matching = [
            e for e in edges
            if e.source_fsm == "MultiLegFsm"
            and e.trigger_event == "CancelRequested"
            and e.target_fsm == "RouteFsm"
            and e.emitted_event == "RouteCancelRequested"
        ]
        assert matching, (
            "MultiLegFsm CancelRequested must emit RouteCancelRequested → RouteFsm"
        )

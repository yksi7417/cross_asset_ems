import type { RefDataRecord, Change, AuditEntry, DomainType, SignoffRole } from "./types";
import { shortHash } from "./utils";

const iso = (d: Date) => d.toISOString();

const days = (n: number) => {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + n);
  return d;
};

const hours = (n: number) => {
  const d = new Date();
  d.setUTCHours(d.getUTCHours() + n);
  return d;
};

export const MOCK_RECORDS: RefDataRecord[] = [
  // counterparty
  { domain: "counterparty", key: "goldman-sachs", scope: "global.default", status: "ACTIVE", version: 7, updatedAt: iso(days(-3)), updatedBy: "k.lopez@globex-trading", hash: shortHash("cp.gs"),
    value: { lei: "5493003OVH1Q2KCA1Q22", name: "The Goldman Sachs Group, Inc.", creditLimitUsd: 250_000_000, settlement: "DVP", jurisdictions: ["US","UK","JP"], tags: ["prime","equity"] } },
  { domain: "counterparty", key: "jp-morgan", scope: "global.default", status: "ACTIVE", version: 5, updatedAt: iso(days(-12)), updatedBy: "k.lopez@globex-trading", hash: shortHash("cp.jpm"),
    value: { lei: "8I5DZWZKVSZI1NUHU748", name: "JPMorgan Chase Bank N.A.", creditLimitUsd: 300_000_000, settlement: "DVP", jurisdictions: ["US","UK","SG","HK"], tags: ["prime","fx"] } },
  { domain: "counterparty", key: "citi", scope: "global.default", status: "ACTIVE", version: 4, updatedAt: iso(days(-30)), updatedBy: "h.müller@acme-capital", hash: shortHash("cp.citi"),
    value: { lei: "E57ODZWZ7FF32TWEFA76", name: "Citibank N.A.", creditLimitUsd: 200_000_000, settlement: "DVP", jurisdictions: ["US","UK"], tags: ["prime","fi"] } },
  { domain: "counterparty", key: "hsbc", scope: "region.hkg", status: "ACTIVE", version: 3, updatedAt: iso(days(-7)), updatedBy: "k.lopez@globex-trading", hash: shortHash("cp.hsbc"),
    value: { lei: "2HI3YI5320LMSRW0TU36", name: "HSBC Holdings plc", creditLimitUsd: 180_000_000, settlement: "DVP", jurisdictions: ["HK","UK"], tags: ["prime","equity","fx"] } },
  { domain: "counterparty", key: "barclays", scope: "region.ldn", status: "ACTIVE", version: 2, updatedAt: iso(days(-21)), updatedBy: "k.lopez@globex-trading", hash: shortHash("cp.bar"),
    value: { lei: "G5GSEF7VJP5I7OUK5573", name: "Barclays Bank plc", creditLimitUsd: 150_000_000, settlement: "DVP", jurisdictions: ["UK","US"], tags: ["prime","equity"] } },
  { domain: "counterparty", key: "morgan-stanley", scope: "global.default", status: "DRAFT", version: 1, updatedAt: iso(days(-1)), updatedBy: "h.müller@acme-capital", hash: shortHash("cp.ms"),
    value: { lei: "IGJSJL3JD5P30I6NJZ34", name: "Morgan Stanley & Co. LLC", creditLimitUsd: 175_000_000, settlement: "DVP", jurisdictions: ["US","UK"], tags: ["prime"] } },

  // broker
  { domain: "broker", key: "gs-equities-routing", scope: "global.default", status: "ACTIVE", version: 12, updatedAt: iso(days(-2)), updatedBy: "p.osei@globex-trading", hash: shortHash("br.gs"),
    value: { counterparty: "goldman-sachs", assetClasses: ["equity"], feeScheduleBps: 0.8, minTicketUsd: 5000, darkParticipation: 0.15 } },
  { domain: "broker", key: "jpm-fx-routing", scope: "region.ldn", status: "ACTIVE", version: 9, updatedAt: iso(days(-5)), updatedBy: "p.osei@globex-trading", hash: shortHash("br.jpm.fx"),
    value: { counterparty: "jp-morgan", assetClasses: ["fx"], feeScheduleBps: 0.2, minTicketUsd: 100000, streamingQuotes: true } },
  { domain: "broker", key: "barclays-eu", scope: "region.ldn", status: "DEPRECATED", version: 4, updatedAt: iso(days(-90)), updatedBy: "p.osei@globex-trading", hash: shortHash("br.bar"),
    value: { counterparty: "barclays", assetClasses: ["equity","fi"], feeScheduleBps: 1.0, minTicketUsd: 10000, darkParticipation: 0.10 } },

  // compliance
  { domain: "compliance", key: "restricted-list-default", scope: "global.default", status: "ACTIVE", version: 21, updatedAt: iso(days(-1)), updatedBy: "compliance@globex-trading", hash: shortHash("co.res"),
    value: { list: ["XYZ-INC","ACME-CO"], reason: "material non-public information", reviewer: "compliance@globex-trading" } },
  { domain: "compliance", key: "max-order-notional-fx", scope: "desk.acme-capital.acme-fx-tokyo", status: "ACTIVE", version: 3, updatedAt: iso(days(-14)), updatedBy: "h.müller@acme-capital", hash: shortHash("co.fx"),
    value: { maxNotionalUsd: 500_000_000, perClipUsd: 25_000_000, killSwitchUsd: 750_000_000 } },
  { domain: "compliance", key: "jurisdiction-allowlist-tyo", scope: "region.tyo", status: "ACTIVE", version: 5, updatedAt: iso(days(-9)), updatedBy: "compliance@globex-trading", hash: shortHash("co.jur"),
    value: { allowed: ["JP","US","SG","HK","AU"], blockOnMismatch: true } },

  // allocation
  { domain: "allocation", key: "default-fifo-equity-ny", scope: "desk.globex-trading.globex-equities-ny", status: "ACTIVE", version: 6, updatedAt: iso(days(-4)), updatedBy: "p.osei@globex-trading", hash: shortHash("al.fifo"),
    value: { method: "FIFO", respectClientInstructions: true, roundLot: 100, minResidualShares: 100 } },
  { domain: "allocation", key: "prorata-fx-tokyo", scope: "desk.acme-capital.acme-fx-tokyo", status: "ACTIVE", version: 2, updatedAt: iso(days(-22)), updatedBy: "h.müller@acme-capital", hash: shortHash("al.pro"),
    value: { method: "PRORATA", toleranceBps: 2, notifyOnResidual: true } },

  // microstructure
  { domain: "microstructure", key: "nyse-equities-tick", scope: "region.ny", status: "ACTIVE", version: 8, updatedAt: iso(days(-6)), updatedBy: "k.lopez@globex-trading", hash: shortHash("ms.tick"),
    value: { tickSize: 0.01, lotSize: 100, maxPriceDeviationBps: 50, allowOddLots: true } },
  { domain: "microstructure", key: "lse-equities-tick", scope: "region.ldn", status: "ACTIVE", version: 5, updatedAt: iso(days(-11)), updatedBy: "k.lopez@globex-trading", hash: shortHash("ms.lse"),
    value: { tickSize: 0.0001, lotSize: 1, maxPriceDeviationBps: 80, allowOddLots: true } },

  // calendars
  { domain: "calendars", key: "nyse-2026", scope: "region.ny", status: "ACTIVE", version: 1, updatedAt: iso(days(-45)), updatedBy: "k.lopez@globex-trading", hash: shortHash("ca.nyse"),
    value: { holidays: ["2026-01-01","2026-01-19","2026-02-16","2026-04-03","2026-05-25","2026-06-19","2026-07-03","2026-09-07","2026-11-26","2026-12-25"], earlyCloses: [{ date: "2026-07-03", close: "13:00" }] } },
  { domain: "calendars", key: "tse-2026", scope: "region.tyo", status: "ACTIVE", version: 1, updatedAt: iso(days(-30)), updatedBy: "k.lopez@globex-trading", hash: shortHash("ca.tse"),
    value: { holidays: ["2026-01-01","2026-01-12","2026-02-11","2026-04-29","2026-05-04","2026-05-05","2026-07-20","2026-08-11","2026-11-03","2026-11-23"], earlyCloses: [] } },

  // curves
  { domain: "curves", key: "usd-discount-ois", scope: "global.default", status: "ACTIVE", version: 14, updatedAt: iso(days(-1)), updatedBy: "risk@globex-trading", hash: shortHash("cu.ois"),
    value: { interpolation: "log-linear", tenors: ["1W","2W","1M","3M","6M","1Y","2Y"], source: "fed-repo" } },
  { domain: "curves", key: "jpy-discount-tonar", scope: "region.tyo", status: "ACTIVE", version: 7, updatedAt: iso(days(-2)), updatedBy: "risk@globex-trading", hash: shortHash("cu.tonar"),
    value: { interpolation: "monotone-convex", tenors: ["1W","1M","3M","6M","1Y"], source: "boj-tonar" } },

  // wheel
  { domain: "wheel", key: "jpy-spot-wheel", scope: "desk.acme-capital.acme-fx-tokyo", status: "ACTIVE", version: 4, updatedAt: iso(days(-8)), updatedBy: "h.müller@acme-capital", hash: shortHash("wh.jpy"),
    value: { dealers: ["goldman-sachs","jp-morgan","citi","barclays"], rotation: "volume-weighted", fairness: { lookbackDays: 30, deviationBps: 5 } } },

  // validator_rules
  { domain: "validator_rules", key: "maintenance-window-prod", scope: "environment.prod", status: "ACTIVE", version: 3, updatedAt: iso(days(-20)), updatedBy: "platform@globex-trading", hash: shortHash("vr.win"),
    value: { window: { dayOfWeek: "SUNDAY", startUtc: "22:00", endUtc: "23:59" }, blockOnViolation: true, code: "EMS-CFG-1301" } },
  { domain: "validator_rules", key: "self-approval-guard", scope: "global.default", status: "ACTIVE", version: 1, updatedAt: iso(days(-180)), updatedBy: "platform@globex-trading", hash: shortHash("vr.self"),
    value: { blockOnViolation: true, code: "EMS-CFG-1201", appliesTo: ["ALL"] } },

  // fsm_definitions
  { domain: "fsm_definitions", key: "order-lifecycle-eq", scope: "asset_class.equity", status: "ACTIVE", version: 5, updatedAt: iso(days(-17)), updatedBy: "k.lopez@globex-trading", hash: shortHash("fsm.eq"),
    value: { states: ["NEW","RISK_OK","ROUTED","PARTIAL","FILLED","CANCELLED","REJECTED"], transitions: [
      { from: "NEW", to: "RISK_OK", on: "pretrade_ok" }, { from: "RISK_OK", to: "ROUTED", on: "select_venue" },
      { from: "ROUTED", to: "PARTIAL", on: "partial_fill" }, { from: "ROUTED", to: "FILLED", on: "full_fill" },
      { from: "PARTIAL", to: "FILLED", on: "full_fill" }, { from: "NEW", to: "REJECTED", on: "pretrade_fail" },
      { from: "ROUTED", to: "CANCELLED", on: "cancel" } ] } },

  // sbe_schemas
  { domain: "sbe_schemas", key: "new-order-single-eq-v3", scope: "asset_class.equity", status: "ACTIVE", version: 3, updatedAt: iso(days(-60)), updatedBy: "platform@globex-trading", hash: shortHash("sbe.nos"),
    value: { template: "sbe/new-order-single-eq.xml", version: 3, messageType: "NOS_EQ", md5: "1f2e3d4c5b6a7980" } },

  // firm_settings
  { domain: "firm_settings", key: "acme-capital", scope: "firm.acme-capital", status: "ACTIVE", version: 9, updatedAt: iso(days(-4)), updatedBy: "h.müller@acme-capital", hash: shortHash("fs.acme"),
    value: { displayName: "Acme Capital", mpid: "ACME", defaultLocale: "en-JP", sessionTimeoutMin: 30, theme: "dark" } },
  { domain: "firm_settings", key: "globex-trading", scope: "firm.globex-trading", status: "ACTIVE", version: 11, updatedAt: iso(days(-2)), updatedBy: "k.lopez@globex-trading", hash: shortHash("fs.glx"),
    value: { displayName: "Globex Trading", mpid: "GLBX", defaultLocale: "en-US", sessionTimeoutMin: 60, theme: "system" } }
];

export const MOCK_CHANGES: Change[] = [
  {
    id: "chg-2026-0017",
    domain: "counterparty",
    key: "morgan-stanley",
    scope: "global.default",
    proposedBy: "h.müller@acme-capital",
    proposedAt: iso(hours(-4)),
    status: "PENDING_APPROVAL",
    expiresAt: iso(hours(20)),
    currentVersion: 0,
    proposedVersion: 1,
    rationale: "Add Morgan Stanley as a prime counterparty following credit committee approval (CC-2026-04). LEI verified, jurisdictions vetted, credit limit set to 175M USD.",
    diff: [
      { path: "lei", op: "add", after: "IGJSJL3JD5P30I6NJZ34" },
      { path: "name", op: "add", after: "Morgan Stanley & Co. LLC" },
      { path: "creditLimitUsd", op: "add", after: 175_000_000 },
      { path: "settlement", op: "add", after: "DVP" },
      { path: "jurisdictions", op: "add", after: ["US","UK"] },
      { path: "tags", op: "add", after: ["prime"] }
    ],
    signoffs: [],
    validators: [
      { code: "EMS-CFG-1201", message: "Proposer may not self-approve.", severity: "BLOCK" }
    ]
  },
  {
    id: "chg-2026-0018",
    domain: "broker",
    key: "jpm-fx-routing",
    scope: "region.ldn",
    proposedBy: "p.osei@globex-trading",
    proposedAt: iso(hours(-26)),
    status: "PENDING_APPROVAL",
    expiresAt: iso(hours(-2)),
    currentVersion: 9,
    proposedVersion: 10,
    rationale: "Reduce FX fee schedule by 0.1 bps following Q1 renegotiation. Volume tier moved from silver to gold.",
    diff: [
      { path: "feeScheduleBps", op: "replace", before: 0.2, after: 0.1 },
      { path: "volumeTier", op: "add", after: "gold" }
    ],
    signoffs: [
      { role: "BEST_EXEC_COMMITTEE", userId: "bec.chair@globex-trading", signedAt: iso(hours(-12)), decision: "APPROVE", comment: "Tier upgrade evidenced by 12M USD monthly notional." }
    ],
    validators: [
      { code: "EMS-CFG-1501", message: "Proposal expired (TTL 24h). Re-propose or extend.", severity: "BLOCK" }
    ]
  },
  {
    id: "chg-2026-0019",
    domain: "compliance",
    key: "max-order-notional-fx",
    scope: "desk.acme-capital.acme-fx-tokyo",
    proposedBy: "h.müller@acme-capital",
    proposedAt: iso(hours(-2)),
    status: "PENDING_APPROVAL",
    expiresAt: iso(hours(46)),
    currentVersion: 3,
    proposedVersion: 4,
    rationale: "Increase per-clip cap for Acme FX Tokyo to support new JPY/SGD desk mandate. Per risk memo, kill-switch unchanged.",
    diff: [
      { path: "perClipUsd", op: "replace", before: 25_000_000, after: 40_000_000 }
    ],
    signoffs: [
      { role: "RISK", userId: "risk.head@acme-capital", signedAt: iso(hours(-1)), decision: "APPROVE", comment: "Within VaR envelope, kill-switch 750M unchanged." }
    ],
    validators: [
      { code: "EMS-CFG-1201", message: "Proposer may not self-approve.", severity: "BLOCK" }
    ]
  },
  {
    id: "chg-2026-0014",
    domain: "wheel",
    key: "jpy-spot-wheel",
    scope: "desk.acme-capital.acme-fx-tokyo",
    proposedBy: "h.müller@acme-capital",
    proposedAt: iso(days(-3)),
    status: "APPROVED",
    expiresAt: iso(days(-2)),
    currentVersion: 3,
    proposedVersion: 4,
    rationale: "Add Morgan Stanley to JPY spot wheel once counterparty record is active.",
    diff: [
      { path: "dealers", op: "replace", before: ["goldman-sachs","jp-morgan","citi","barclays"], after: ["goldman-sachs","jp-morgan","citi","barclays","morgan-stanley"] }
    ],
    signoffs: [
      { role: "COMPLIANCE_OFFICER", userId: "compliance@acme-capital", signedAt: iso(days(-3)), decision: "APPROVE" },
      { role: "RISK", userId: "risk.head@acme-capital", signedAt: iso(days(-3)), decision: "APPROVE" },
      { role: "BEST_EXEC_COMMITTEE", userId: "bec@acme-capital", signedAt: iso(days(-2)), decision: "APPROVE", comment: "Fairness recalculated post-add." }
    ],
    validators: []
  },
  {
    id: "chg-2026-0011",
    domain: "microstructure",
    key: "nyse-equities-tick",
    scope: "region.ny",
    proposedBy: "k.lopez@globex-trading",
    proposedAt: iso(days(-1)),
    status: "REJECTED",
    expiresAt: iso(days(0)),
    currentVersion: 8,
    proposedVersion: 9,
    rationale: "Relax odd-lot handling on NYSE — proposal withdrawn by ops.",
    diff: [
      { path: "allowOddLots", op: "replace", before: true, after: false }
    ],
    signoffs: [
      { role: "BEST_EXEC_COMMITTEE", userId: "bec@globex-trading", signedAt: iso(hours(-6)), decision: "REJECT", comment: "Tighter rounding would regress best-ex metrics for retail flow." }
    ],
    validators: [
      { code: "EMS-CFG-1301", message: "Production scope change outside maintenance window.", severity: "BLOCK" }
    ]
  },
  {
    id: "chg-2026-0020",
    domain: "firm_settings",
    key: "globex-trading",
    scope: "firm.globex-trading",
    proposedBy: "k.lopez@globex-trading",
    proposedAt: iso(hours(-30)),
    status: "DRAFT",
    expiresAt: iso(hours(18)),
    currentVersion: 11,
    proposedVersion: 12,
    rationale: "Reduce session timeout from 60 to 45 minutes per infosec recommendation (SEC-2026-019).",
    diff: [
      { path: "sessionTimeoutMin", op: "replace", before: 60, after: 45 }
    ],
    signoffs: [],
    validators: [
      { code: "EMS-CFG-1201", message: "Proposer may not self-approve.", severity: "BLOCK" }
    ]
  }
];

export const MOCK_AUDIT: AuditEntry[] = [
  { id: "aud-1", changeId: "chg-2026-0014", domain: "wheel", key: "jpy-spot-wheel", action: "PROPOSE", actor: "h.müller@acme-capital", role: "AUTHOR", at: iso(days(-3)) },
  { id: "aud-2", changeId: "chg-2026-0014", domain: "wheel", key: "jpy-spot-wheel", action: "APPROVE", actor: "compliance@acme-capital", role: "COMPLIANCE_OFFICER", at: iso(days(-3)) },
  { id: "aud-3", changeId: "chg-2026-0014", domain: "wheel", key: "jpy-spot-wheel", action: "APPROVE", actor: "risk.head@acme-capital", role: "RISK", at: iso(days(-3)) },
  { id: "aud-4", changeId: "chg-2026-0014", domain: "wheel", key: "jpy-spot-wheel", action: "APPROVE", actor: "bec@acme-capital", role: "BEST_EXEC_COMMITTEE", at: iso(days(-2)) },
  { id: "aud-5", changeId: "chg-2026-0014", domain: "wheel", key: "jpy-spot-wheel", action: "APPLY", actor: "system", role: "SYSTEM", at: iso(days(-2)), fromVersion: 3, toVersion: 4 },
  { id: "aud-6", changeId: "chg-2026-0011", domain: "microstructure", key: "nyse-equities-tick", action: "PROPOSE", actor: "k.lopez@globex-trading", role: "AUTHOR", at: iso(days(-1)) },
  { id: "aud-7", changeId: "chg-2026-0011", domain: "microstructure", key: "nyse-equities-tick", action: "REJECT", actor: "bec@globex-trading", role: "BEST_EXEC_COMMITTEE", at: iso(hours(-6)), note: "Best-ex regression." },
  { id: "aud-8", changeId: "chg-2026-0017", domain: "counterparty", key: "morgan-stanley", action: "PROPOSE", actor: "h.müller@acme-capital", role: "AUTHOR", at: iso(hours(-4)) },
  { id: "aud-9", changeId: "chg-2026-0018", domain: "broker", key: "jpm-fx-routing", action: "PROPOSE", actor: "p.osei@globex-trading", role: "AUTHOR", at: iso(hours(-26)) },
  { id: "aud-10", changeId: "chg-2026-0018", domain: "broker", key: "jpm-fx-routing", action: "APPROVE", actor: "bec.chair@globex-trading", role: "BEST_EXEC_COMMITTEE", at: iso(hours(-12)) },
  { id: "aud-11", changeId: "chg-2026-0019", domain: "compliance", key: "max-order-notional-fx", action: "PROPOSE", actor: "h.müller@acme-capital", role: "AUTHOR", at: iso(hours(-2)) },
  { id: "aud-12", changeId: "chg-2026-0019", domain: "compliance", key: "max-order-notional-fx", action: "APPROVE", actor: "risk.head@acme-capital", role: "RISK", at: iso(hours(-1)) },
  { id: "aud-13", changeId: "chg-2026-0020", domain: "firm_settings", key: "globex-trading", action: "PROPOSE", actor: "k.lopez@globex-trading", role: "AUTHOR", at: iso(hours(-30)) }
];

// Required signoff roles per domain (heuristic from arch doc).
export const REQUIRED_SIGNOFFS: Record<DomainType, SignoffRole[]> = {
  counterparty:    ["COMPLIANCE_OFFICER"],
  broker:          ["BEST_EXEC_COMMITTEE"],
  compliance:      ["COMPLIANCE_OFFICER", "RISK"],
  allocation:      ["BEST_EXEC_COMMITTEE"],
  microstructure:  ["BEST_EXEC_COMMITTEE", "RISK"],
  calendars:       [],
  curves:          ["RISK"],
  wheel:           ["COMPLIANCE_OFFICER", "RISK", "BEST_EXEC_COMMITTEE"],
  validator_rules: ["COMPLIANCE_OFFICER"],
  fsm_definitions: [],
  sbe_schemas:     [],
  firm_settings:   []
};
import type { DomainType, ScopeLevel, SignoffRole } from "./types";

export const REGISTRIES: { domain: DomainType; title: string; description: string; icon: string }[] = [
  { domain: "counterparty", title: "Counterparty", description: "Legal entities, LEIs, credit limits, settlement instructions.", icon: "Building2" },
  { domain: "broker", title: "Broker", description: "Broker configuration, routing tags, fee schedules.", icon: "Radio" },
  { domain: "compliance", title: "Compliance", description: "Pre-trade rules, restricted lists, jurisdictional controls.", icon: "ShieldCheck" },
  { domain: "allocation", title: "Allocation", description: "Block-level allocation profiles, FIFO, prorata, custom.", icon: "GitSplit" },
  { domain: "microstructure", title: "Microstructure", description: "Venue tick sizes, lot rules, order book params.", icon: "Layers" },
  { domain: "calendars", title: "Calendars", description: "Trading calendars, holidays, session windows.", icon: "Calendar" },
  { domain: "curves", title: "Curves", description: "Discount and forward curves, interpolation methods.", icon: "TrendingUp" },
  { domain: "wheel", title: "Wheel", description: "Order wheel definitions, dealer order, fairness.", icon: "CircleDot" },
  { domain: "validator_rules", title: "Validator Rules", description: "EMS-CFG-* validation rules and maintenance windows.", icon: "ShieldAlert" },
  { domain: "fsm_definitions", title: "FSM Definitions", description: "Order/risk state machine definitions and transitions.", icon: "Workflow" },
  { domain: "sbe_schemas", title: "SBE Schemas", description: "FIX/SBE schema references and version pins.", icon: "Binary" },
  { domain: "firm_settings", title: "Firm Settings", description: "Per-firm defaults, logos, identifiers, locale.", icon: "Settings" }
];

export const SCOPE_LEVELS: { id: ScopeLevel; description: string; ordinal: number }[] = [
  { id: "global.default", description: "Baseline values visible to all environments.", ordinal: 0 },
  { id: "environment.dev", description: "Non-production, ephemeral.", ordinal: 1 },
  { id: "environment.qa", description: "QA regression environment.", ordinal: 2 },
  { id: "environment.uat", description: "User acceptance testing.", ordinal: 3 },
  { id: "environment.prod", description: "Production, governed by change window.", ordinal: 4 },
  { id: "region.ny", description: "New York region override.", ordinal: 5 },
  { id: "region.ldn", description: "London region override.", ordinal: 6 },
  { id: "region.tyo", description: "Tokyo region override.", ordinal: 7 },
  { id: "region.hkg", description: "Hong Kong region override.", ordinal: 8 },
  { id: "pod.pod-east-1a", description: "Pod-level override (example).", ordinal: 9 },
  { id: "asset_class.equity", description: "Asset class override: equity.", ordinal: 10 },
  { id: "asset_class.fi", description: "Asset class override: fixed income.", ordinal: 11 },
  { id: "asset_class.fx", description: "Asset class override: FX.", ordinal: 12 },
  { id: "firm.acme-capital", description: "Firm override: Acme Capital.", ordinal: 13 },
  { id: "firm.globex-trading", description: "Firm override: Globex Trading.", ordinal: 14 },
  { id: "desk.acme-capital.acme-fx-tokyo", description: "Desk override: Acme FX Tokyo.", ordinal: 15 },
  { id: "desk.globex-trading.globex-equities-ny", description: "Desk override: Globex Equities NY.", ordinal: 16 },
  { id: "user.acme-capital.acme-fx-tokyo.tanaka", description: "User override (example).", ordinal: 17 },
  { id: "order-override", description: "Single-order override (not stored post-trade).", ordinal: 18 }
];

export const ROLE_LABELS: Record<SignoffRole, { title: string; description: string }> = {
  COMPLIANCE_OFFICER: {
    title: "Compliance Officer",
    description: "Attests jurisdictional and conduct rules; required for counterparty, compliance, microstructure changes."
  },
  RISK: {
    title: "Risk",
    description: "Attests risk model, limits, and exposure impact; required for compliance, microstructure, curves, wheel changes."
  },
  BEST_EXEC_COMMITTEE: {
    title: "Best Ex. Committee",
    description: "Attests best execution policy adherence; required for broker, wheel, microstructure, allocation changes."
  }
};

export const VALIDATOR_DESCRIPTIONS: Record<string, string> = {
  "EMS-CFG-1001": "Unknown key — key not declared in schema for this domain.",
  "EMS-CFG-1201": "Self-approval not allowed — proposer cannot also sign off.",
  "EMS-CFG-1301": "Outside maintenance window — production scope changes require window.",
  "EMS-CFG-1302": "Effective-in-past not allowed for production scopes.",
  "EMS-CFG-1401": "Deletion forbidden — record is referenced by live order/route history.",
  "EMS-CFG-1501": "Proposal expired — TTL exceeded without required signoffs."
};
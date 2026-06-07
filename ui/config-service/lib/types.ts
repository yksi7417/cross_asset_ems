// Configuration Service domain types. See arch-configuration-service.md.

export type DomainType =
  | "counterparty"
  | "broker"
  | "compliance"
  | "allocation"
  | "microstructure"
  | "calendars"
  | "curves"
  | "wheel"
  | "validator_rules"
  | "fsm_definitions"
  | "sbe_schemas"
  | "firm_settings";

export type ScopeLevel =
  | "global.default"
  | "environment.dev"
  | "environment.qa"
  | "environment.uat"
  | "environment.prod"
  | "region.ny"
  | "region.ldn"
  | "region.tyo"
  | "region.hkg"
  | `pod.${string}`
  | "asset_class.equity"
  | "asset_class.fi"
  | "asset_class.fx"
  | `firm.${string}`
  | `desk.${string}.${string}`
  | `user.${string}.${string}.${string}`
  | "order-override";

export type RecordStatus = "ACTIVE" | "DRAFT" | "DEPRECATED" | "PENDING_APPROVAL";

export type SignoffRole = "COMPLIANCE_OFFICER" | "RISK" | "BEST_EXEC_COMMITTEE";

export type ValidatorCode =
  | "EMS-CFG-1001"
  | "EMS-CFG-1201"
  | "EMS-CFG-1301"
  | "EMS-CFG-1302"
  | "EMS-CFG-1401"
  | "EMS-CFG-1501";

export interface RefDataRecord {
  domain: DomainType;
  key: string;
  value: Record<string, unknown>;
  scope: ScopeLevel;
  status: RecordStatus;
  version: number;
  updatedAt: string; // ISO
  updatedBy: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  hash: string;
}

export interface Signoff {
  role: SignoffRole;
  userId: string;
  signedAt: string;
  decision: "APPROVE" | "REJECT";
  comment?: string;
}

export interface Change {
  id: string;
  domain: DomainType;
  key: string;
  scope: ScopeLevel;
  proposedBy: string;
  proposedAt: string;
  status: "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "EXPIRED";
  expiresAt: string;
  currentVersion: number;
  proposedVersion: number;
  diff: { path: string; op: "add" | "remove" | "replace"; before?: unknown; after?: unknown }[];
  signoffs: Signoff[];
  validators: { code: ValidatorCode; message: string; severity: "BLOCK" | "WARN" }[];
  rationale: string;
}

export interface AuditEntry {
  id: string;
  changeId: string;
  domain: DomainType;
  key: string;
  action: "PROPOSE" | "APPROVE" | "REJECT" | "APPLY" | "EXPIRE" | "WITHDRAW";
  actor: string;
  role?: SignoffRole | "AUTHOR" | "SYSTEM";
  at: string;
  fromVersion?: number;
  toVersion?: number;
  note?: string;
}
import { cn } from "@/lib/utils";
import type { RecordStatus } from "@/lib/types";
import type { ScopeLevel } from "@/lib/types";
import { scopeBadge } from "@/lib/format";

const recordTone: Record<RecordStatus, string> = {
  ACTIVE: "bg-success/10 text-success border-success/30",
  DRAFT: "bg-muted text-muted-foreground border-border",
  DEPRECATED: "bg-warning/10 text-warning border-warning/30",
  PENDING_APPROVAL: "bg-primary/10 text-primary border-primary/30"
};

export function StatusBadge({ status }: { status: RecordStatus }) {
  return (
    <span className={cn("text-[11px] px-1.5 py-0.5 rounded border font-medium", recordTone[status])}>
      {status}
    </span>
  );
}

const scopeTone: Record<string, string> = {
  global: "bg-foreground/5 text-foreground border-border",
  env: "bg-primary/10 text-primary border-primary/30",
  region: "bg-accent text-accent-foreground border-border",
  asset: "bg-secondary text-secondary-foreground border-border",
  firm: "bg-warning/10 text-warning border-warning/30",
  desk: "bg-primary/5 text-primary border-primary/20",
  user: "bg-muted text-muted-foreground border-border",
  override: "bg-destructive/10 text-destructive border-destructive/30"
};

export function ScopeBadge({ scope }: { scope: ScopeLevel }) {
  const b = scopeBadge(scope);
  return (
    <span className={cn("text-[11px] px-1.5 py-0.5 rounded border font-mono", scopeTone[b.tone])}>
      {b.label}
    </span>
  );
}
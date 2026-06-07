import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

export function Card({
  title, subtitle, actions, className, children
}: { title?: ReactNode; subtitle?: ReactNode; actions?: ReactNode; className?: string; children?: ReactNode }) {
  return (
    <div className={cn("rounded-lg border border-border bg-card text-card-foreground shadow-sm", className)}>
      {(title || actions) && (
        <div className="px-4 py-3 border-b border-border flex items-start justify-between gap-3">
          <div>
            {title && <div className="text-sm font-semibold">{title}</div>}
            {subtitle && <div className="mt-0.5">{subtitle}</div>}
          </div>
          {actions}
        </div>
      )}
      {children && <div className="p-4">{children}</div>}
    </div>
  );
}
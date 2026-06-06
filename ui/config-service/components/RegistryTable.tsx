import Link from "next/link";
import { StatusBadge, ScopeBadge } from "./StatusBadge";
import type { RefDataRecord } from "@/lib/types";
import { fmtDateShort, relativeTime } from "@/lib/format";
import { ArrowUpRight } from "lucide-react";

export function RegistryTable({ records, domain }: { records: RefDataRecord[]; domain: string }) {
  if (!records.length) {
    return (
      <div className="text-sm text-muted-foreground border border-dashed border-border rounded-md p-8 text-center">
        No records in this registry yet.
      </div>
    );
  }
  return (
    <div className="rounded-md border border-border overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-muted/50 text-muted-foreground">
          <tr>
            <th className="text-left font-medium px-3 py-2">Key</th>
            <th className="text-left font-medium px-3 py-2">Scope</th>
            <th className="text-left font-medium px-3 py-2">Status</th>
            <th className="text-right font-medium px-3 py-2">Version</th>
            <th className="text-left font-medium px-3 py-2">Updated</th>
            <th className="text-left font-medium px-3 py-2">By</th>
            <th className="w-8" />
          </tr>
        </thead>
        <tbody>
          {records.map((r) => (
            <tr key={`${r.domain}:${r.key}:${r.version}`} className="border-t border-border hover:bg-accent/40">
              <td className="px-3 py-2">
                <Link href={`/registries/${r.domain}/${encodeURIComponent(r.key)}`} className="font-mono text-primary hover:underline">
                  {r.key}
                </Link>
              </td>
              <td className="px-3 py-2"><ScopeBadge scope={r.scope} /></td>
              <td className="px-3 py-2"><StatusBadge status={r.status} /></td>
              <td className="px-3 py-2 text-right tabular-nums">v{r.version}</td>
              <td className="px-3 py-2 text-muted-foreground">
                <span title={r.updatedAt}>{fmtDateShort(r.updatedAt)} · {relativeTime(r.updatedAt)}</span>
              </td>
              <td className="px-3 py-2 text-muted-foreground font-mono text-xs">{r.updatedBy}</td>
              <td className="px-2 py-2 text-muted-foreground">
                <Link href={`/registries/${r.domain}/${encodeURIComponent(r.key)}`} className="inline-flex">
                  <ArrowUpRight className="h-4 w-4" />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
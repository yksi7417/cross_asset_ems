import { Plus, Minus, Replace } from "lucide-react";

interface DiffOp { path: string; op: "add" | "remove" | "replace"; before?: unknown; after?: unknown }

function fmt(v: unknown): string {
  if (v === undefined) return "∅";
  if (typeof v === "string") return JSON.stringify(v);
  if (typeof v === "number" || typeof v === "boolean") return String(v);
  return JSON.stringify(v, null, 2);
}

export function VersionDiff({ diff }: { diff: DiffOp[] }) {
  if (!diff.length) {
    return <div className="text-sm text-muted-foreground italic">No changes.</div>;
  }
  return (
    <div className="rounded-md border border-border overflow-hidden">
      <table className="w-full text-xs font-mono">
        <thead className="bg-muted/50 text-muted-foreground">
          <tr>
            <th className="text-left font-medium px-3 py-2 w-20">Op</th>
            <th className="text-left font-medium px-3 py-2 w-1/3">Path</th>
            <th className="text-left font-medium px-3 py-2 w-1/3">Before</th>
            <th className="text-left font-medium px-3 py-2 w-1/3">After</th>
          </tr>
        </thead>
        <tbody>
          {diff.map((d, i) => {
            const Icon = d.op === "add" ? Plus : d.op === "remove" ? Minus : Replace;
            const opColor =
              d.op === "add" ? "text-success" : d.op === "remove" ? "text-destructive" : "text-warning";
            return (
              <tr key={i} className="border-t border-border align-top">
                <td className={`px-3 py-2 ${opColor} flex items-center gap-1`}>
                  <Icon className="h-3.5 w-3.5" /> {d.op}
                </td>
                <td className="px-3 py-2">{d.path}</td>
                <td className="px-3 py-2 whitespace-pre-wrap break-all text-muted-foreground">
                  {d.op !== "add" ? fmt(d.before) : <span className="italic opacity-60">—</span>}
                </td>
                <td className="px-3 py-2 whitespace-pre-wrap break-all">
                  {d.op !== "remove" ? fmt(d.after) : <span className="italic opacity-60">—</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
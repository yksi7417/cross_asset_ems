import Link from "next/link";
import { Card } from "@/components/Card";
import { MOCK_RECORDS } from "@/lib/mock-data";
import { REGISTRIES } from "@/lib/constants";
import { ArrowRight, Database } from "lucide-react";

export default function RegistriesPage() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Registries</h1>
        <p className="text-sm text-muted-foreground">
          {REGISTRIES.length} reference-data domains. Each registry is a versioned, scoped collection of records.
        </p>
      </header>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {REGISTRIES.map((r) => {
          const recs = MOCK_RECORDS.filter((x) => x.domain === r.domain);
          const active = recs.filter((x) => x.status === "ACTIVE").length;
          const draft = recs.filter((x) => x.status === "DRAFT").length;
          return (
            <Link key={r.domain} href={`/registries/${r.domain}`}>
              <Card className="h-full hover:border-primary/40 transition-colors">
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-2">
                    <Database className="h-4 w-4 text-primary" />
                    <div className="font-semibold">{r.title}</div>
                  </div>
                  <ArrowRight className="h-4 w-4 text-muted-foreground" />
                </div>
                <p className="text-xs text-muted-foreground mt-1">{r.description}</p>
                <div className="mt-3 flex items-center gap-2 text-xs">
                  <span className="text-muted-foreground">{recs.length} records</span>
                  <span className="px-1.5 py-0.5 rounded bg-success/10 text-success border border-success/30">{active} active</span>
                  {draft > 0 && <span className="px-1.5 py-0.5 rounded bg-muted text-muted-foreground border border-border">{draft} draft</span>}
                </div>
                <div className="mt-2 text-[11px] text-muted-foreground font-mono">{r.domain}</div>
              </Card>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
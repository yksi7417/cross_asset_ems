"use client";
import { use } from "react";
import { useRegistry } from "@/lib/api";
import { RegistryTable } from "@/components/RegistryTable";
import { Card } from "@/components/Card";
import { REGISTRIES } from "@/lib/constants";
import type { DomainType } from "@/lib/types";
import { notFound } from "next/navigation";
import { Skeleton } from "@/components/Skeleton";

export default function RegistryPage({ params }: { params: Promise<{ domain: string }> }) {
  const { domain } = use(params);
  const meta = REGISTRIES.find((r) => r.domain === domain);
  if (!meta) notFound();
  const { data, isLoading, error } = useRegistry(domain as DomainType);
  return (
    <div className="space-y-4">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-semibold">{meta.title}</h1>
          <p className="text-sm text-muted-foreground">{meta.description}</p>
          <p className="text-xs text-muted-foreground font-mono mt-1">{meta.domain}</p>
        </div>
        <div className="text-sm text-muted-foreground">{data?.length ?? 0} records</div>
      </header>
      <Card>
        {isLoading && <Skeleton rows={5} />}
        {error && <div className="text-sm text-destructive">Failed to load: {String(error)}</div>}
        {data && <RegistryTable records={data} domain={meta.domain} />}
      </Card>
    </div>
  );
}
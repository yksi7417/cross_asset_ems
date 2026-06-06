import useSWR, { mutate } from "swr";
import type { RefDataRecord, Change, AuditEntry, DomainType } from "./types";

const fetcher = async (url: string) => {
  const r = await fetch(url, { cache: "no-store" });
  if (!r.ok) throw new Error(`Request failed: ${r.status}`);
  return r.json();
};

export function useRegistries() {
  return useSWR<{ domain: DomainType; count: number; active: number; draft: number; pending: number }[]>(
    "/api/registries", fetcher
  );
}

export function useRegistry(domain: DomainType) {
  return useSWR<RefDataRecord[]>(`/api/registries/${domain}`, fetcher);
}

export function useRecord(domain: DomainType, key: string) {
  return useSWR<{ current: RefDataRecord | null; history: RefDataRecord[]; change: Change | null }>(
    `/api/registries/${domain}/${encodeURIComponent(key)}`, fetcher
  );
}

export function useApprovals() {
  return useSWR<Change[]>("/api/approvals", fetcher);
}

export function useChanges() {
  return useSWR<AuditEntry[]>("/api/changes", fetcher);
}

export async function signChange(id: string, body: { role: string; userId: string; decision: "APPROVE" | "REJECT"; comment?: string }) {
  const r = await fetch(`/api/approvals/${id}/sign`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!r.ok) throw new Error(`Sign failed: ${r.status}`);
  await Promise.all([mutate("/api/approvals"), mutate("/api/changes")]);
  return r.json();
}
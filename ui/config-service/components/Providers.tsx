"use client";
import { SWRConfig } from "swr";

const fetcher = (url: string) => fetch(url, { cache: "no-store" }).then((r) => {
  if (!r.ok) throw new Error(`Request failed: ${r.status}`);
  return r.json();
});

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <SWRConfig value={{ fetcher, revalidateOnFocus: false, dedupingInterval: 1000 }}>
      {children}
    </SWRConfig>
  );
}
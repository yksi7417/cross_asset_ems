'use client';

import { ReactNode } from 'react';
import { SWRConfig } from 'swr';

const swrFetcher = async <T,>(url: string): Promise<T> => {
  const res = await fetch(url);
  if (!res.ok) {
    const err = new Error(`Request failed: ${res.status}`) as Error & {
      status?: number;
      info?: unknown;
    };
    err.status = res.status;
    try {
      err.info = await res.json();
    } catch {
      // body not json
    }
    throw err;
  }
  return (await res.json()) as T;
};

export function Providers({ children }: { children: ReactNode }) {
  return (
    <SWRConfig
      value={{
        fetcher: swrFetcher,
        revalidateOnFocus: false,
        revalidateIfStale: true,
        shouldRetryOnError: true,
        errorRetryCount: 2,
      }}
    >
      {children}
    </SWRConfig>
  );
}
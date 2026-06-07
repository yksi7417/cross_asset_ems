import type { Metadata } from "next";
import { Nav } from "@/components/Nav";
import { Providers } from "@/components/Providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "Config Service · Admin",
  description: "Internal admin console for reference-data registries, change-workflow sign-off, and version history."
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen bg-background text-foreground">
        <Providers>
          <div className="flex min-h-screen">
            <Nav />
            <main className="flex-1 min-w-0">
              <div className="h-14 border-b border-border px-6 flex items-center justify-between bg-card">
                <div className="text-sm text-muted-foreground">cross_asset_ems / ui / config-service</div>
                <div className="text-xs text-muted-foreground font-mono">env: dev · region: ny</div>
              </div>
              <div className="p-6 max-w-7xl mx-auto">{children}</div>
            </main>
          </div>
        </Providers>
      </body>
    </html>
  );
}
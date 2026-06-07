import type { Metadata } from 'next';
import { Providers } from '@/components/Providers';
import { Nav } from '@/components/Nav';
import './globals.css';

export const metadata: Metadata = {
  title: 'Time/Replay Console — cross-asset-ems',
  description: 'Internal admin console for the Time/Replay Server',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <body className="bg-slate-950 text-slate-100 antialiased">
        <Providers>
          <div className="flex h-screen overflow-hidden">
            <Nav />
            <main className="flex-1 overflow-auto">{children}</main>
          </div>
        </Providers>
      </body>
    </html>
  );
}
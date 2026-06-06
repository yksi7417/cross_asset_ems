"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { Layers, ShieldCheck, CheckSquare, History, LayoutDashboard, Settings2 } from "lucide-react";
import { useUi } from "@/lib/store";

const items = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/registries", label: "Registries", icon: Layers },
  { href: "/approvals", label: "Approvals", icon: CheckSquare },
  { href: "/changes", label: "Audit log", icon: History }
];

export function Nav() {
  const path = usePathname();
  const user = useUi((s) => s.user);
  return (
    <aside className="w-60 shrink-0 border-r border-border bg-card flex flex-col">
      <div className="h-14 flex items-center gap-2 px-4 border-b border-border">
        <Settings2 className="h-5 w-5 text-primary" />
        <div className="leading-tight">
          <div className="text-sm font-semibold">Config Service</div>
          <div className="text-[11px] text-muted-foreground">Admin console</div>
        </div>
      </div>
      <nav className="flex-1 p-2 space-y-0.5">
        {items.map((it) => {
          const active = it.href === "/" ? path === "/" : path?.startsWith(it.href);
          const Icon = it.icon;
          return (
            <Link
              key={it.href}
              href={it.href}
              className={cn(
                "flex items-center gap-2 rounded-md px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground",
                active && "bg-accent text-accent-foreground font-medium"
              )}
            >
              <Icon className="h-4 w-4" />
              {it.label}
            </Link>
          );
        })}
      </nav>
      <div className="p-3 border-t border-border">
        <div className="text-[11px] text-muted-foreground">Signed in as</div>
        <div className="text-sm font-medium">{user.displayName}</div>
        <div className="text-[11px] text-muted-foreground">{user.firm} · {user.desk}</div>
        <div className="mt-2 flex flex-wrap gap-1">
          {user.roles.map((r) => (
            <span key={r} className="text-[10px] px-1.5 py-0.5 rounded bg-primary/10 text-primary border border-primary/20">
              {r}
            </span>
          ))}
        </div>
      </div>
    </aside>
  );
}
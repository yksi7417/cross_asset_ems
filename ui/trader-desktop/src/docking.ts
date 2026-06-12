/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// VSCode-style dockable layout (18.24) on dockview-core.
//
// Evaluation (PLAN 18.24): @finos/perspective-workspace gives drag/dock/split/tab natively
// around Perspective viewers — but ONLY viewers, and four of our panels are not grids (ticket,
// ESP tiles, watchlist header controls, notifications). Dockview hosts arbitrary DOM panels
// with the exact VSCode interaction model (drag tabs to split/dock/stack, resize splitters),
// zero dependencies, and JSON layout serialization. Decision: dockview-core.
//
// Mechanics: the panel <section> elements stay where index.html defines them (all the
// getElementById wiring keeps working); at logon each section is MOVED into a dockview panel.
// Custom elements (perspective-viewer) tolerate re-parenting, and Perspective observes its own
// size, so splitter drags re-render the grids. Layouts persist per user/desk and restore on the
// next logon; RESET LAYOUT in the top bar returns to the default arrangement.

import { createDockview, type DockviewApi } from "dockview-core";
import "dockview-core/dist/styles/dockview.css";

interface PanelDef {
  id: string;
  title: string;
  sectionId: string;
}

// Today's arrangement, expressed as dock positions (left tool column + blotter stack).
const PANELS: PanelDef[] = [
  { id: "ticket", title: "ORDER TICKET", sectionId: "ticket-panel" },
  { id: "esp", title: "CLICK TO TRADE", sectionId: "esp-panel" },
  { id: "watchlist", title: "WATCHLIST", sectionId: "watchlist-panel" },
  { id: "notify", title: "NOTIFICATIONS", sectionId: "notify-panel" },
  { id: "orders", title: "ORDER BLOTTER", sectionId: "orders-panel" },
  { id: "routes", title: "ROUTES", sectionId: "routes-panel" },
  { id: "fills", title: "FILLS", sectionId: "fills-panel" },
  { id: "baskets", title: "BASKETS", sectionId: "baskets-panel" },
  { id: "pnl", title: "INTRADAY P&L", sectionId: "pnl-panel" },
];

let api: DockviewApi | null = null;
let persistKey = "ems-dock/anon";

// Off-screen stash: panel teardown (reset/relayout) destroys dockview containers, which would
// take the adopted <section> elements with them — rescue them here first, re-adopt after.
function stash(): HTMLElement {
  let el = document.getElementById("dock-stash");
  if (!el) {
    el = document.createElement("div");
    el.id = "dock-stash";
    el.style.display = "none";
    document.body.append(el);
  }
  return el;
}

function rescueSections(): void {
  const safe = stash();
  for (const def of PANELS) {
    const section = document.getElementById(def.sectionId);
    if (section) {
      safe.append(section);
    }
  }
}

export function initDocking(user: string, desk: string): void {
  persistKey = `ems-dock/${user}@${desk}`;
  const main = document.querySelector("main")!;
  main.classList.add("docked");
  const host = document.createElement("div");
  host.id = "dock-root";
  main.append(host);

  api = createDockview(host, {
    className: "dockview-theme-abyss",
    // Keep inactive tab content ATTACHED (hidden, not removed): the app wires panels by
    // getElementById, and Perspective viewers must stay in the document to keep streaming —
    // the default "onlyWhenVisible" renderer detaches inactive panels and broke both.
    defaultRenderer: "always",
    createComponent: () => {
      const element = document.createElement("div");
      element.className = "dock-section-host";
      return {
        element,
        init: (params) => {
          const sectionId = (params.params as { sectionId?: string }).sectionId;
          const section = sectionId ? document.getElementById(sectionId) : null;
          if (section) {
            element.append(section); // move, don't clone — all wiring stays live
          }
        },
      };
    },
  });

  const saved = localStorage.getItem(persistKey);
  let restored = false;
  if (saved) {
    try {
      api.fromJSON(JSON.parse(saved));
      restored = true;
    } catch {
      restored = false; // stale/incompatible layout — fall back to default
    }
  }
  if (!restored) {
    defaultLayout(api);
  }

  // The old static columns are now empty shells — drop them.
  document.getElementById("left-col")?.remove();
  document.getElementById("right-col")?.remove();

  api.onDidLayoutChange(() => {
    if (api) {
      localStorage.setItem(persistKey, JSON.stringify(api.toJSON()));
    }
  });

  document.getElementById("dock-reset")?.addEventListener("click", resetLayout);
}

function defaultLayout(dock: DockviewApi): void {
  const add = (
    id: string,
    position?: { referencePanel: string; direction: "left" | "right" | "above" | "below" | "within" },
  ) => {
    const def = PANELS.find((p) => p.id === id)!;
    return dock.addPanel({
      id,
      component: "section",
      title: def.title,
      params: { sectionId: def.sectionId },
      ...(position ? { position } : {}),
    });
  };
  // Order matters: split the window into left tools / right blotters FIRST, then stack within
  // each region — a "below ticket" added before the right split would span the full width.
  const ticket = add("ticket");
  const orders = add("orders", { referencePanel: "ticket", direction: "right" });
  add("esp", { referencePanel: "ticket", direction: "below" });
  add("watchlist", { referencePanel: "esp", direction: "below" });
  add("notify", { referencePanel: "watchlist", direction: "below" });
  // All four lower grids side by side (no default tab-stacking: Perspective paints nothing
  // into a hidden viewer, so a tabbed-away blotter looks empty until activated — let the USER
  // choose to stack panels, the default keeps every grid painting).
  add("routes", { referencePanel: "orders", direction: "below" });
  add("fills", { referencePanel: "routes", direction: "right" });
  add("baskets", { referencePanel: "fills", direction: "right" });
  add("pnl", { referencePanel: "baskets", direction: "right" });
  ticket.api.setSize({ width: 340 });
  orders.api.setSize({ height: Math.round(window.innerHeight * 0.42) });
}

/** Return to the default arrangement (the top-bar RESET LAYOUT button). */
export function resetLayout(): void {
  if (!api) {
    return;
  }
  localStorage.removeItem(persistKey);
  rescueSections(); // panel teardown destroys containers — save the sections first
  for (const panel of [...api.panels]) {
    api.removePanel(panel);
  }
  defaultLayout(api);
}

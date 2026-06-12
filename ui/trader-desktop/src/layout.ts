/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// Column picker + layout persistence (18.23).
//
// Perspective's settings panel IS the column picker (drag columns in/out, group, sort, filter) —
// it was just hidden behind an undiscoverable toolbar toggle. Every grid panel now gets a
// visible ⚙ COLUMNS button (toggleConfig) and a ⟲ reset, and every user change persists to
// localStorage per user/desk/viewer, restored on the next logon.
//
// Two invariants:
//  - INTERACTION KEYS STAY CONFIGURED. Selection, linking and context menus read rows through
//    the view, which only carries configured columns — hiding orderId/routeId/figi would break
//    right-click/linking (the recurring Phase-18 lesson). Saved layouts are merged with each
//    grid's required keys before applying.
//  - LINK FILTERS ARE TRANSIENT. The blotter-linking filter is runtime state, not layout —
//    it is stripped before saving, and applyFilter() keeps working over a custom layout because
//    the persisted config becomes the viewer's base config.
//
// Adding a new column to a blotter is ONE place: add the field to the grid's schema (and the
// server row if it's new data) in main.ts — the picker then offers it; users place it.

type SavableViewer = HTMLElement & {
  save(): Promise<Record<string, unknown>>;
  restore(config: object): Promise<void>;
  toggleConfig(): Promise<void>;
};

let scope = "anon"; // per user/desk, set at logon

/** Scope persisted layouts to the logged-on user/desk. */
export function setLayoutScope(user: string, desk: string): void {
  scope = `${user}@${desk}`;
}

const storageKey = (viewerId: string) => `ems-layout/${scope}/${viewerId}`;

/** The saved layout for a viewer, with required interaction keys re-asserted. */
export function savedLayout(
  viewerId: string,
  requiredColumns: string[],
): Record<string, unknown> | null {
  const raw = localStorage.getItem(storageKey(viewerId));
  if (!raw) {
    return null;
  }
  try {
    const layout = JSON.parse(raw) as Record<string, unknown>;
    if (Array.isArray(layout.columns)) {
      const columns = layout.columns as string[];
      layout.columns = [...columns, ...requiredColumns.filter((c) => !columns.includes(c))];
    }
    delete layout.filter; // link filters are transient runtime state
    return layout;
  } catch {
    return null;
  }
}

/**
 * Wire the ⚙/⟲ header buttons and persistence for one grid.
 *
 * @param onBaseChange called with the persisted config whenever the user changes the layout —
 *     main.ts updates the viewer's base config so link filters re-apply over the custom layout.
 */
export function attachColumnControls(
  viewerId: string,
  requiredColumns: string[],
  baseConfig: Record<string, unknown>,
  onBaseChange: (config: Record<string, unknown>) => void,
): void {
  const viewer = document.getElementById(viewerId) as SavableViewer;
  const header = viewer.closest("section.panel")?.querySelector("h2");
  if (header && !header.querySelector(".col-btn")) {
    const reset = document.createElement("button");
    reset.className = "col-btn";
    reset.title = "Reset this grid's layout";
    reset.textContent = "⟲";
    reset.addEventListener("click", () => {
      localStorage.removeItem(storageKey(viewerId));
      onBaseChange(baseConfig);
      void viewer.restore(baseConfig);
    });
    const cols = document.createElement("button");
    cols.className = "col-btn";
    cols.title = "Show/hide columns, group, sort, filter (Perspective settings)";
    cols.textContent = "⚙ COLUMNS";
    cols.addEventListener("click", () => void viewer.toggleConfig());
    header.append(cols, reset);
  }

  let applying = false; // ignore the config-update echo from our own restore calls
  viewer.addEventListener("perspective-config-update", () => {
    if (applying) {
      return;
    }
    void (async () => {
      const saved = await viewer.save();
      delete saved.filter; // transient (blotter linking)
      delete saved.settings; // whether the panel is open is not layout
      if (Array.isArray(saved.columns)) {
        const columns = saved.columns as string[];
        saved.columns = [...columns, ...requiredColumns.filter((c) => !columns.includes(c))];
      }
      localStorage.setItem(storageKey(viewerId), JSON.stringify(saved));
      onBaseChange({ ...baseConfig, ...saved });
    })();
  });

  // Apply the persisted layout (if any) over the base config.
  const layout = savedLayout(viewerId, requiredColumns);
  if (layout) {
    const merged = { ...baseConfig, ...layout };
    onBaseChange(merged);
    applying = true;
    void viewer.restore(merged).finally(() => {
      applying = false;
    });
  }
}

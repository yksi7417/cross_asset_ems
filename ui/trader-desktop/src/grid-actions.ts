/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// Grid interactions (18.17 feedback #3/#5): row selection and right-click context menus over
// Perspective viewers. The datagrid emits `perspective-click` with the full row on left click;
// we accumulate a selection (plain click = select one, ctrl/cmd+click = toggle into a
// multi-selection) and a right-click opens a menu of actions over the current selection.
// Perspective owns the grid DOM, so selection state lives here and is surfaced via a chip.

export type GridRow = Record<string, unknown>;

export interface GridAction {
  /** Menu label; receives the selection size, e.g. (n) => `Cancel (${n})`. */
  label: (n: number) => string;
  run: (rows: GridRow[]) => void | Promise<void>;
}

export interface GridInteractionOptions {
  /** Row identity field (orderId / routeId). */
  keyField: string;
  /** Called on plain (single) click — drives blotter linking. */
  onPrimary?: (row: GridRow) => void;
  /** Context-menu actions over the selection. */
  actions: GridAction[];
  /** Element that displays the live selection count. */
  chip: HTMLElement;
}

const menu = () => document.getElementById("ctx-menu")!;

function closeMenu(): void {
  menu().classList.add("hidden");
}

document.addEventListener("click", closeMenu);
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    closeMenu();
  }
});

export function attachGridInteractions(
  viewerId: string,
  options: GridInteractionOptions,
): { clear: () => void } {
  const viewer = document.getElementById(viewerId)!;
  const selected = new Map<string, GridRow>();
  let lastModifier = false;

  function renderChip(): void {
    options.chip.textContent = selected.size > 1 ? `${selected.size} selected` : "";
  }

  function clear(): void {
    selected.clear();
    renderChip();
  }

  // perspective-click is a CustomEvent without mouse modifiers; remember them from the
  // preceding mousedown (capture phase reaches us before Perspective swallows the event).
  viewer.addEventListener(
    "mousedown",
    (e) => {
      lastModifier = (e as MouseEvent).ctrlKey || (e as MouseEvent).metaKey;
    },
    true,
  );

  viewer.addEventListener("perspective-click", (e) => {
    const row = (e as CustomEvent).detail?.row as GridRow | undefined;
    const key = row?.[options.keyField] as string | undefined;
    if (!row || !key) {
      return;
    }
    if (lastModifier) {
      // ctrl/cmd+click: toggle membership in the multi-selection.
      if (selected.has(key)) {
        selected.delete(key);
      } else {
        selected.set(key, row);
      }
    } else {
      selected.clear();
      selected.set(key, row);
      options.onPrimary?.(row);
    }
    renderChip();
  });

  viewer.addEventListener("contextmenu", (e) => {
    e.preventDefault();
    const m = menu();
    m.replaceChildren();
    if (selected.size === 0) {
      const hint = document.createElement("div");
      hint.className = "ctx-hint";
      hint.textContent = "Click a row first (ctrl+click to multi-select)";
      m.append(hint);
    } else {
      const header = document.createElement("div");
      header.className = "ctx-hint";
      header.textContent =
        selected.size === 1
          ? String(selected.keys().next().value)
          : `${selected.size} rows selected`;
      m.append(header);
      for (const action of options.actions) {
        const item = document.createElement("button");
        item.textContent = action.label(selected.size);
        item.addEventListener("click", () => {
          closeMenu();
          void action.run([...selected.values()]);
        });
        m.append(item);
      }
    }
    m.classList.remove("hidden");
    const me = e as MouseEvent;
    m.style.left = `${Math.min(me.clientX, window.innerWidth - m.offsetWidth - 8)}px`;
    m.style.top = `${Math.min(me.clientY, window.innerHeight - m.offsetHeight - 8)}px`;
  });

  return { clear };
}

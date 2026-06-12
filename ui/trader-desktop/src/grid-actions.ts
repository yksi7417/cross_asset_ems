/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// Grid selection + context menus (18.17, rebuilt in 18.20 to desktop-grade semantics):
//
//   click               select the row under the pointer (drives blotter linking)
//   ctrl/cmd + click    toggle the row into a multi-selection
//   shift + click       range-select from the anchor to the pointer row
//   arrows / page keys  move the selection; with shift, extend the range from the anchor
//   right-click         POINTER TRUTH: if the row under the cursor is not in the selection,
//                       it becomes the selection — actions always hit what you clicked on
//
// Selected rows are highlighted in the grid itself. Perspective's datagrid re-renders on every
// scroll/stream update, so highlighting re-applies from a style listener; selection is keyed by
// row identity (orderId/routeId/figi), so it survives re-sorts and streaming updates.
//
// Implementation notes: the datagrid's <regular-table> lives behind open shadow roots; we find it
// by traversal, read row indices via table.getMeta(td).y, and resolve indices to full rows through
// the viewer's view (view.to_json range queries return all CONFIGURED columns, painted or not).

export type GridRow = Record<string, unknown>;

export interface GridAction {
  /** Menu label; receives the count of APPLICABLE rows, e.g. (n) => `Cancel (${n})`. */
  label: (n: number) => string;
  /** State filter: which selected rows this action can act on (18.18: state-aware menus). */
  applicable?: (row: GridRow) => boolean;
  run: (rows: GridRow[]) => void | Promise<void>;
}

export interface GridInteractionOptions {
  /** Row identity field (orderId / routeId / figi). Must be a configured column. */
  keyField: string;
  /** Short row label for the selection chip (defaults to the key). */
  rowLabel?: (row: GridRow) => string;
  /** Called on plain (single) click — drives blotter linking. */
  onPrimary?: (row: GridRow) => void;
  /** Context-menu actions over the selection. */
  actions: GridAction[];
  /** Element that displays the live selection. */
  chip: HTMLElement;
}

type ViewerEl = HTMLElement & {
  getView(): Promise<{
    num_rows(): Promise<number>;
    to_json(opts: { start_row: number; end_row: number }): Promise<GridRow[]>;
  }>;
};

type RegularTableEl = HTMLElement & {
  getMeta(el: Element): { x?: number; y?: number } | undefined;
  addStyleListener(cb: () => void): void;
  scrollToCell(x: number, y: number, ncols: number, nrows: number): Promise<void>;
};

const HIGHLIGHT_CSS = `
  tbody tr.ems-sel td { background: rgba(63, 182, 139, 0.16) !important; }
  tbody tr.ems-sel td:first-of-type { box-shadow: inset 3px 0 0 #3fb68b; }
`;

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

/** Depth-first search across open shadow roots. */
function findDeep(root: ParentNode, selector: string): Element | null {
  const direct = root.querySelector(selector);
  if (direct) {
    return direct;
  }
  for (const el of root.querySelectorAll("*")) {
    if (el.shadowRoot) {
      const found = findDeep(el.shadowRoot, selector);
      if (found) {
        return found;
      }
    }
  }
  return null;
}

export function attachGridInteractions(
  viewerId: string,
  options: GridInteractionOptions,
): { clear: () => void } {
  const viewer = document.getElementById(viewerId) as ViewerEl;
  viewer.tabIndex = 0; // keyboard selection target
  const selected = new Map<string, GridRow>();
  let table: RegularTableEl | null = null;
  let anchorY: number | null = null; // range anchor (last non-shift selection)
  let focusY: number | null = null; // keyboard focus row
  let highlightToken = 0;

  const keyOf = (row: GridRow) => String(row[options.keyField]);

  // ── view access ──────────────────────────────────────────────────────────────

  async function rowsRange(a: number, b: number): Promise<{ start: number; rows: GridRow[] }> {
    const view = await viewer.getView();
    const total = await view.num_rows();
    const start = Math.max(0, Math.min(a, b));
    const end = Math.min(total, Math.max(a, b) + 1);
    return {
      start,
      rows: end > start ? await view.to_json({ start_row: start, end_row: end }) : [],
    };
  }

  async function rowAt(y: number): Promise<GridRow | undefined> {
    return (await rowsRange(y, y)).rows[0];
  }

  // ── selection state ──────────────────────────────────────────────────────────

  function renderChip(): void {
    if (selected.size === 0) {
      options.chip.textContent = "";
    } else if (selected.size === 1) {
      const row = selected.values().next().value as GridRow;
      options.chip.textContent = `▸ ${options.rowLabel?.(row) ?? keyOf(row)}`;
    } else {
      options.chip.textContent = `▸ ${selected.size} selected`;
    }
    scheduleHighlight();
  }

  function selectOnly(y: number, row: GridRow): void {
    selected.clear();
    selected.set(keyOf(row), row);
    anchorY = y;
    focusY = y;
    renderChip();
  }

  function toggle(y: number, row: GridRow): void {
    const key = keyOf(row);
    if (selected.has(key)) {
      selected.delete(key);
    } else {
      selected.set(key, row);
    }
    anchorY = y;
    focusY = y;
    renderChip();
  }

  async function selectRange(fromY: number, toY: number): Promise<void> {
    const { rows } = await rowsRange(fromY, toY);
    selected.clear();
    for (const row of rows) {
      selected.set(keyOf(row), row);
    }
    focusY = toY;
    renderChip();
  }

  function clear(): void {
    selected.clear();
    anchorY = null;
    focusY = null;
    renderChip();
  }

  // ── highlight (re-applied on every datagrid draw) ────────────────────────────

  function scheduleHighlight(): void {
    const token = ++highlightToken;
    void (async () => {
      if (!table) {
        return;
      }
      const trs = [...table.querySelectorAll("tbody tr")];
      const metas = trs.map((tr) =>
        tr.firstElementChild ? table!.getMeta(tr.firstElementChild) : undefined,
      );
      const ys = metas.map((m) => m?.y).filter((y): y is number => typeof y === "number");
      if (ys.length === 0) {
        return;
      }
      const { start, rows } = await rowsRange(Math.min(...ys), Math.max(...ys));
      if (token !== highlightToken) {
        return; // a newer draw superseded this pass
      }
      trs.forEach((tr, i) => {
        const y = metas[i]?.y;
        const row = typeof y === "number" ? rows[y - start] : undefined;
        tr.classList.toggle("ems-sel", !!row && selected.has(keyOf(row)));
      });
    })();
  }

  // ── wire the table once it exists (the datagrid renders asynchronously) ─────

  function locateTable(): void {
    // The datagrid renders <regular-table> inside a DESCENDANT's shadow root — walk the light
    // DOM (descending into child shadow roots) first, then the viewer's own shadow root.
    table =
      (findDeep(viewer, "regular-table") as RegularTableEl | null) ??
      (viewer.shadowRoot
        ? (findDeep(viewer.shadowRoot, "regular-table") as RegularTableEl | null)
        : null);
    if (!table) {
      setTimeout(locateTable, 500);
      return;
    }
    const styleHost = table.getRootNode();
    const host = styleHost instanceof ShadowRoot ? styleHost : document.head;
    if (!host.querySelector("style[data-ems-sel]")) {
      const style = document.createElement("style");
      style.setAttribute("data-ems-sel", "");
      style.textContent = HIGHLIGHT_CSS;
      host.append(style);
    }
    table.addStyleListener(scheduleHighlight);

    table.addEventListener("click", (e) => {
      void handlePointer(e, false);
    });
    table.addEventListener("contextmenu", (e) => {
      e.preventDefault();
      void handlePointer(e, true);
    });
  }
  locateTable();

  function rowIndexFromEvent(e: MouseEvent): number | undefined {
    if (!table) {
      return undefined;
    }
    const td = e
      .composedPath()
      .find((n): n is Element => n instanceof Element && n.tagName === "TD");
    if (!td) {
      return undefined;
    }
    const meta = table.getMeta(td);
    return typeof meta?.y === "number" ? meta.y : undefined;
  }

  /** Re-read every selected row's CURRENT state from the view (snapshots go stale on stream). */
  async function refreshSelection(): Promise<void> {
    if (selected.size === 0) {
      return;
    }
    const view = await viewer.getView();
    const total = await view.num_rows();
    const live = await view.to_json({ start_row: 0, end_row: total });
    for (const row of live) {
      const key = keyOf(row);
      if (selected.has(key)) {
        selected.set(key, row);
      }
    }
  }

  async function handlePointer(e: MouseEvent, isContext: boolean): Promise<void> {
    const y = rowIndexFromEvent(e);
    if (y === undefined) {
      return;
    }
    viewer.focus();
    const row = await rowAt(y);
    if (!row) {
      return;
    }
    if (isContext) {
      // Pointer truth: a right-click outside the current selection re-selects under the cursor.
      if (!selected.has(keyOf(row))) {
        selectOnly(y, row);
      }
      await refreshSelection();
      openMenu(e);
      return;
    }
    if (e.shiftKey && anchorY !== null) {
      await selectRange(anchorY, y);
    } else if (e.ctrlKey || e.metaKey) {
      toggle(y, row);
    } else {
      selectOnly(y, row);
      options.onPrimary?.(row);
    }
  }

  // ── keyboard selection ───────────────────────────────────────────────────────

  viewer.addEventListener("keydown", (e) => {
    if (focusY === null) {
      return; // nothing selected yet — let the grid handle its own keys
    }
    const page = Math.max(5, (table?.querySelectorAll("tbody tr").length ?? 16) - 1);
    const delta =
      e.key === "ArrowDown" ? 1
      : e.key === "ArrowUp" ? -1
      : e.key === "PageDown" ? page
      : e.key === "PageUp" ? -page
      : 0;
    if (delta === 0) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    void (async () => {
      const view = await viewer.getView();
      const total = await view.num_rows();
      const target = Math.max(0, Math.min(total - 1, (focusY ?? 0) + delta));
      if (e.shiftKey && anchorY !== null) {
        await selectRange(anchorY, target);
      } else {
        const row = await rowAt(target);
        if (row) {
          selectOnly(target, row);
        }
      }
      try {
        await table?.scrollToCell(0, Math.max(0, target - 2), 1, 1);
      } catch {
        // best-effort scroll; selection itself is already updated
      }
    })();
  });

  // ── context menu ─────────────────────────────────────────────────────────────

  function openMenu(e: MouseEvent): void {
    const m = menu();
    m.replaceChildren();
    if (selected.size === 0) {
      const hint = document.createElement("div");
      hint.className = "ctx-hint";
      hint.textContent = "Click a row first (ctrl+click / shift+click to multi-select)";
      m.append(hint);
    } else {
      const header = document.createElement("div");
      header.className = "ctx-hint";
      header.textContent =
        selected.size === 1
          ? (options.rowLabel?.(selected.values().next().value as GridRow) ??
            String(selected.keys().next().value))
          : `${selected.size} rows selected`;
      m.append(header);
      for (const action of options.actions) {
        const rows = [...selected.values()].filter((r) => action.applicable?.(r) ?? true);
        const item = document.createElement("button");
        item.textContent = action.label(rows.length);
        if (rows.length === 0) {
          // Visible but inert: the user learns WHY nothing would happen instead of a 0/N toast.
          item.disabled = true;
          item.textContent += " — no applicable rows";
        } else {
          item.addEventListener("click", () => {
            closeMenu();
            void action.run(rows);
          });
        }
        m.append(item);
      }
    }
    m.classList.remove("hidden");
    m.style.left = `${Math.min(e.clientX, window.innerWidth - m.offsetWidth - 8)}px`;
    m.style.top = `${Math.min(e.clientY, window.innerHeight - m.offsetHeight - 8)}px`;
  }

  return { clear };
}

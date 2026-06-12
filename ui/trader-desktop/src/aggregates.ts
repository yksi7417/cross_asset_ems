/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */

// MULTI badge for grouped blotters (18.22): identity-ish columns aggregate with Perspective's
// "unique" — the shared value when all children agree, null when they differ. A trader reads
// "MULTI", not an empty cell, so on every datagrid draw we tag the null aggregate cells whose
// column opted into same-or-MULTI semantics. Under group_by EVERY rendered row is an aggregate
// (rows carry a tree <th>; even the deepest level is `psp-tree-leaf`, an aggregate over its
// records — individual records never render as rows when grouped), so the row filter is simply
// tr:has(th); ungrouped rows have no th and keep their genuine blanks.
//
// Known edge: a group whose children are ALL null in such a column (e.g. limit px on a group of
// pure market orders) also reads MULTI — the engine's unique-aggregate output is null either way.

import { findDeep } from "./grid-actions";

const MULTI_CSS = `
  tbody tr td.ems-multi::before {
    content: "MULTI";
    opacity: 0.45;
    font-size: 9px;
    letter-spacing: 0.5px;
  }
`;

type RegularTableEl = HTMLElement & {
  getMeta(el: Element): { column_header?: unknown[] } | undefined;
  addStyleListener(cb: () => void): void;
};

/** Tag mixed-value aggregate cells in group rows of `viewerId` with a MULTI badge. */
export function attachMultiBadge(viewerId: string, columns: ReadonlySet<string>): void {
  const viewer = document.getElementById(viewerId)!;

  function locate(): void {
    const table =
      (findDeep(viewer, "regular-table") as RegularTableEl | null) ??
      (viewer.shadowRoot
        ? (findDeep(viewer.shadowRoot, "regular-table") as RegularTableEl | null)
        : null);
    if (!table) {
      setTimeout(locate, 500);
      return;
    }
    const root = table.getRootNode();
    const host = root instanceof ShadowRoot ? root : document.head;
    if (!host.querySelector("style[data-ems-multi]")) {
      const style = document.createElement("style");
      style.setAttribute("data-ems-multi", "");
      style.textContent = MULTI_CSS;
      host.append(style);
    }
    table.addStyleListener(() => {
      for (const tr of table.querySelectorAll("tbody tr:has(th)")) {
        for (const td of tr.querySelectorAll("td.psp-null")) {
          const header = table.getMeta(td)?.column_header;
          const column = String(header?.[header.length - 1] ?? "");
          td.classList.toggle("ems-multi", columns.has(column));
        }
      }
    });
  }
  locate();
}

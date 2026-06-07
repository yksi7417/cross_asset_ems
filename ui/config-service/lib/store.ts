"use client";
import { create } from "zustand";
import type { SignoffRole } from "./types";

export interface CurrentUser {
  userId: string;
  displayName: string;
  firm: string;
  desk: string;
  roles: SignoffRole[];
}

interface UiState {
  user: CurrentUser;
  setUser: (u: Partial<CurrentUser>) => void;
  sidebarOpen: boolean;
  setSidebarOpen: (v: boolean) => void;
}

const DEFAULT_USER: CurrentUser = {
  userId: "k.lopez@globex-trading",
  displayName: "Karina Lopez",
  firm: "globex-trading",
  desk: "globex-equities-ny",
  roles: ["COMPLIANCE_OFFICER"] // demo: the logged-in user can sign as compliance
};

export const useUi = create<UiState>((set) => ({
  user: DEFAULT_USER,
  setUser: (u) => set((s) => ({ user: { ...s.user, ...u } })),
  sidebarOpen: true,
  setSidebarOpen: (v) => set({ sidebarOpen: v })
}));
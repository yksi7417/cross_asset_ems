'use client';

import { create } from 'zustand';
import { ClockMode, EventFilter } from './types';

interface FilterStoreState {
  filter: EventFilter;
  setFilter: (filter: EventFilter) => void;
  updateFilter: (patch: Partial<EventFilter>) => void;
  resetFilter: () => void;
}

const initial: EventFilter = { limit: 100 };

export const useFilterStore = create<FilterStoreState>((set) => ({
  filter: initial,
  setFilter: (filter) => set({ filter }),
  updateFilter: (patch) => set((s) => ({ filter: { ...s.filter, ...patch } })),
  resetFilter: () => set({ filter: initial }),
}));

interface NewReplayFormState {
  name: string;
  start_event_id: string;
  end_event_id: string;
  speed: number;
  clock_mode: ClockMode;
  rule_set_version: string;
  code_version_target: string;
  setField: <K extends keyof NewReplayFormState>(key: K, value: NewReplayFormState[K]) => void;
  reset: () => void;
}

const initialForm: Omit<NewReplayFormState, 'setField' | 'reset'> = {
  name: '',
  start_event_id: '',
  end_event_id: '',
  speed: 50,
  clock_mode: 'WALL_CLOCK_FAST',
  rule_set_version: 'rv-2024.09.18',
  code_version_target: 'ems-3.14.2',
};

export const useNewReplayStore = create<NewReplayFormState>((set) => ({
  ...initialForm,
  setField: (key, value) => set((s) => ({ ...s, [key]: value })),
  reset: () => set(() => ({ ...initialForm })),
}));
// In-memory diagnosis history with occurrence counts, mirroring the IntelliJ history service. Pure
// (no vscode API) so it is unit tested directly. De-duplicates by grouping key: the same error seen
// again increments its count and moves to the front (newest first) rather than adding a duplicate.
import { DiagnosisCard, groupingKey } from './engine';

export interface HistoryEntry {
  card: DiagnosisCard;
  count: number;
}

const DEFAULT_MAX = 30;

export class DiagnosisHistory {
  private entriesList: HistoryEntry[] = [];
  private listeners: Array<() => void> = [];

  constructor(private maxEntries: number = DEFAULT_MAX) {}

  /** Adds a card, coalescing repeats by grouping key. Returns true if it was a new (first-seen) entry. */
  add(card: DiagnosisCard): boolean {
    const key = groupingKey(card);
    const existingIdx = this.entriesList.findIndex((e) => groupingKey(e.card) === key);
    let isNew: boolean;
    if (existingIdx >= 0) {
      const existing = this.entriesList[existingIdx];
      existing.count += 1;
      this.entriesList.splice(existingIdx, 1);
      this.entriesList.unshift(existing);
      isNew = false;
    } else {
      this.entriesList.unshift({ card, count: 1 });
      isNew = true;
    }
    if (this.entriesList.length > this.maxEntries) {
      this.entriesList.length = this.maxEntries;
    }
    this.emit();
    return isNew;
  }

  entries(): HistoryEntry[] {
    return this.entriesList.slice();
  }

  clear(): void {
    this.entriesList = [];
    this.emit();
  }

  setMaxEntries(max: number): void {
    this.maxEntries = max;
    if (this.entriesList.length > max) {
      this.entriesList.length = max;
      this.emit();
    }
  }

  onChange(listener: () => void): void {
    this.listeners.push(listener);
  }

  private emit(): void {
    for (const l of this.listeners) l();
  }
}

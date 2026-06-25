// Port of com.springdebugger.service.LogFileTailService. Tails every discovered Spring Boot log file
// and diagnoses errors as they are appended, the terminal-agnostic capture path for bootRun. Each
// file is followed by byte offset, reset on truncation/rotation; the watched set is re-discovered
// periodically; dedup is per file and per run (cleared when a new run starts) so an identical error
// re-surfaces on a re-run but a steady error within one run is shown once. Only the latest run's
// slice is diagnosed so stale errors from earlier runs in an appending file are never surfaced.
//
// The pure tailing logic is separated from the timer so it is unit tested directly.
import * as fs from 'fs';
import {
  ConsoleDiagnoser,
  DiagnosisCard,
  containsRunStart,
  containsErrorSignature,
  groupingKey,
  lastRunSlice,
} from '../engine';

const POLL_MS = 1500;
const REDISCOVER_EVERY = 6;
const BUFFER_MAX_CHARS = 200_000;

export class LogTailWatcher {
  private readonly watched = new Map<string, string>();
  private readonly offsets = new Map<string, number>();
  private readonly buffers = new Map<string, string>();
  private readonly seenByFile = new Map<string, Set<string>>();
  private sinceDiscovery = 0;
  private timer: ReturnType<typeof setInterval> | undefined;

  constructor(
    private readonly diagnoser: ConsoleDiagnoser,
    private readonly resolveLogFiles: () => string[],
  ) {}

  /** Initialises the watched set, baselining existing files at EOF so old content is not replayed. */
  baseline(): void {
    this.watched.clear();
    this.offsets.clear();
    this.buffers.clear();
    this.seenByFile.clear();
    this.sinceDiscovery = 0;
    for (const f of this.resolveLogFiles()) {
      this.watched.set(f, f);
      this.offsets.set(f, fileSize(f) ?? 0);
    }
  }

  /** One poll over all watched files; returns the newly-surfaced cards (after per-file-per-run dedup). */
  pollOnce(): DiagnosisCard[] {
    if (++this.sinceDiscovery >= REDISCOVER_EVERY) {
      this.sinceDiscovery = 0;
      this.rediscover();
    }
    const cards: DiagnosisCard[] = [];
    for (const f of this.watched.values()) {
      try {
        cards.push(...this.tailOne(f));
      } catch {
        // ignore a transient read error; the next poll re-reads
      }
    }
    return cards;
  }

  start(onCards: (cards: DiagnosisCard[]) => void, intervalMs = POLL_MS): void {
    if (this.timer) return;
    this.baseline();
    this.timer = setInterval(() => {
      const cards = this.pollOnce();
      if (cards.length > 0) onCards(cards);
    }, intervalMs);
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
    this.watched.clear();
  }

  isTailing(): boolean {
    return this.timer !== undefined;
  }

  tailedFiles(): string[] {
    return Array.from(this.watched.values());
  }

  /** Adds files declared since baseline. A newly-appearing file is read from the beginning (offset 0). */
  private rediscover(): void {
    for (const f of this.resolveLogFiles()) {
      if (!this.watched.has(f)) {
        this.watched.set(f, f);
        this.offsets.set(f, 0);
      }
    }
  }

  private tailOne(file: string): DiagnosisCard[] {
    const length = fileSize(file);
    if (length === null) return [];
    let previous = this.offsets.get(file) ?? 0;
    if (length < previous) {
      // Truncated or rotated: restart from the beginning of the new file.
      previous = 0;
      this.buffers.delete(file);
    }
    if (length === previous) return [];

    const delta = readFrom(file, previous, length);
    this.offsets.set(file, length);
    if (delta === '') return [];

    let buffer = (this.buffers.get(file) ?? '') + delta;
    if (buffer.length > BUFFER_MAX_CHARS) {
      buffer = buffer.slice(buffer.length - BUFFER_MAX_CHARS);
    }
    this.buffers.set(file, buffer);

    let seen = this.seenByFile.get(file);
    if (!seen) {
      seen = new Set<string>();
      this.seenByFile.set(file, seen);
    }
    // A new run started in this chunk: forget what the previous run showed.
    if (containsRunStart(delta)) seen.clear();
    if (!containsErrorSignature(delta)) return [];

    const currentRun = lastRunSlice(buffer);
    const shown: DiagnosisCard[] = [];
    for (const card of this.diagnoser.diagnoseAll(currentRun)) {
      const key = groupingKey(card);
      if (seen.has(key)) continue;
      seen.add(key);
      shown.push(card);
    }
    return shown;
  }
}

function fileSize(file: string): number | null {
  try {
    return fs.statSync(file).size;
  } catch {
    return null;
  }
}

function readFrom(file: string, from: number, to: number): string {
  let start = from;
  let len = to - start;
  if (len <= 0) return '';
  if (len > BUFFER_MAX_CHARS) {
    start = to - BUFFER_MAX_CHARS;
    len = BUFFER_MAX_CHARS;
  }
  const buf = Buffer.alloc(len);
  const fd = fs.openSync(file, 'r');
  try {
    fs.readSync(fd, buf, 0, len, start);
  } finally {
    fs.closeSync(fd);
  }
  return buf.toString('utf8');
}

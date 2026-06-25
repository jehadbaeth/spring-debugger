// Port of com.springdebugger.service.TestResultsWatchService. Polls the project's JUnit result
// files and diagnoses new failures. Polling (not fs.watch) is the safe default because build/ is
// frequently excluded from editor file watchers, the same reason the IntelliJ version polls. The
// pure pollOnce/baseline logic is separated from the timer so it is unit tested directly.
import * as fs from 'fs';
import { ConsoleDiagnoser, DiagnosisCard, failureTexts, groupingKey, hasFailures, locate } from '../engine';

const POLL_MS = 4000;

export class TestResultsWatcher {
  private lastSeen = new Map<string, number>();
  private timer: ReturnType<typeof setInterval> | undefined;

  constructor(
    private readonly diagnoser: ConsoleDiagnoser,
    private readonly baseProvider: () => string | undefined,
  ) {}

  /** Records current files and their mtimes without diagnosing, so only later runs surface. */
  baseline(): void {
    this.lastSeen.clear();
    for (const f of this.files()) {
      const m = mtime(f);
      if (m !== null) this.lastSeen.set(f, m);
    }
  }

  /**
   * Diagnoses the result files written since the last poll (new path or newer mtime), de-duplicated
   * across the whole batch so one test run that rewrites many suite files surfaces each error once.
   */
  pollOnce(): DiagnosisCard[] {
    const changed: string[] = [];
    for (const f of this.files()) {
      const m = mtime(f);
      if (m === null) continue;
      const previous = this.lastSeen.get(f);
      this.lastSeen.set(f, m);
      if (previous === undefined || previous !== m) changed.push(f);
    }
    if (changed.length === 0) return [];

    const seen = new Set<string>();
    const cards: DiagnosisCard[] = [];
    for (const f of changed) {
      const content = read(f);
      if (content === null || !hasFailures(content)) continue;
      for (const failure of failureTexts(content)) {
        for (const card of this.diagnoser.diagnoseAll(failure)) {
          const key = groupingKey(card);
          if (seen.has(key)) continue;
          seen.add(key);
          cards.push(card);
        }
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
  }

  isWatching(): boolean {
    return this.timer !== undefined;
  }

  private files(): string[] {
    const base = this.baseProvider();
    return base ? locate(base) : [];
  }
}

function mtime(file: string): number | null {
  try {
    return fs.statSync(file).mtimeMs;
  } catch {
    return null;
  }
}

function read(file: string): string | null {
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return null;
  }
}

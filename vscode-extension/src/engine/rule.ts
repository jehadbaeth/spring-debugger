// Port of com.springdebugger.rule.Rule and SignalCriteria. All criteria fields are optional; a
// rule fires when every present field matches (AND). Null/undefined means "not constrained".
import { Confidence, Phase } from './models';

/** Mirrors the signals block in spring-boot-rules.yaml. */
export interface SignalCriteria {
  causedByClass?: string | null;
  causedByMessage?: string | null;
  messageContains?: string | null;
  messageContainsAny?: string[] | null;
  bannerDescriptionContains?: string | null;
  bannerActionContains?: string | null;
  buildLineContains?: string | null;
  exceptionClass?: string | null;
  httpStatus?: number;
}

/** One entry in spring-boot-rules.yaml. */
export interface Rule {
  id: string;
  name: string | null;
  phases: Phase[] | null;
  taps: string[];
  signals: SignalCriteria | null;
  diagnosis: string | null;
  fix: string | null;
  confidence: Confidence;
  fixture: string | null;
  status: string | null;
}

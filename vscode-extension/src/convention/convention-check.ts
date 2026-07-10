// Port of com.springdebugger.convention.ConventionCheck. Each implementation is a pure function of
// the file text plus the rule's params: no IO, no cross-file resolution, deterministic and testable.
import { ConventionRule } from './convention-rule';
import { Violation } from './violation';

export interface ConventionCheck {
  /** The checkType string this implementation handles, matching the catalog's `checkType` field. */
  checkType(): string;

  /** Find every violation of the given rule in the given file text. */
  check(text: string, fileName: string, rule: ConventionRule): Violation[];
}

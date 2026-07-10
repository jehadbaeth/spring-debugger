// Port of com.springdebugger.convention.Violation. One convention violation found by a
// ConventionCheck: an absolute text range to highlight plus the already-interpolated message/fix.
import { TextRange } from './robot-suite';

export interface Violation {
  range: TextRange;
  message: string;
  fix: string | null;
}

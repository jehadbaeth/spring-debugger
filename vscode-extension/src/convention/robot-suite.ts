// Port of com.springdebugger.convention.robot.RobotSuite. The parsed shape of a Robot Framework
// suite file, holding only what the convention checks need. Produced by parseRobotSuite(); ranges
// are absolute offsets into the file text so they can anchor a Diagnostic range directly.

export interface TextRange {
  start: number;
  end: number;
}

export interface RobotMetadata {
  name: string;
  value: string;
  lineRange: TextRange;
}

export interface RobotTestCase {
  name: string;
  nameRange: TextRange;
  hasDocumentation: boolean;
  hasTags: boolean;
  tags: string[];
}

export interface RobotSuite {
  settingsHeaderRange: TextRange | null;
  metadata: RobotMetadata[];
  testCases: RobotTestCase[];
  hasTestCasesSection: boolean;
}

/** First metadata entry whose name matches (ignoring case, spaces, and dashes), or null. */
export function findMetadata(suite: RobotSuite, name: string): RobotMetadata | null {
  const want = normalize(name);
  for (const m of suite.metadata) {
    if (normalize(m.name) === want) return m;
  }
  return null;
}

/** Normalize a metadata name so "Pass-Fail Criteria" and "Pass Fail Criteria" compare equal. */
export function normalize(s: string): string {
  let out = '';
  for (const c of s.toLowerCase()) {
    if (/[a-z0-9]/.test(c)) out += c;
  }
  return out;
}

// Enrichment contracts, ported from com.springdebugger.enricher.*. An enricher is a second pass
// over a rule-matched card that can confirm a structural claim or query the running app and return
// a sharper / higher-confidence card. It must never throw; on any failure it returns the input card
// unchanged so the offline result is preserved.
//
// Async note: unlike the Java version (synchronous PSI + HttpClient), every context lookup here is
// async (VS Code symbol provider and Node HTTP are async), so enrich() returns a Promise.
import { DiagnosisCard, RawSignal } from './models';

export interface Enricher {
  enrich(card: DiagnosisCard, signal: RawSignal, context: EnrichmentContext): Promise<DiagnosisCard>;
}

/** The adapter through which enrichers reach the workspace source and the running application. */
export interface EnrichmentContext {
  /** Structural facts about a class by simple or fully qualified name; null when not resolvable. */
  findClass(name: string): Promise<ClassFacts | null>;
  /** Packages that host an @SpringBootApplication main class (the component-scan roots). */
  springBootApplicationPackages(): Promise<string[]>;
  /** HTTP GET against a running application endpoint (Actuator); null when unreachable or failing. */
  httpGet(url: string): Promise<string | null>;
}

/** A diagnosis engine (the LLM fallback implements this); mirrors Java DiagnosisEngine. */
export interface DiagnosisEngine {
  diagnose(signal: RawSignal): Promise<DiagnosisCard | null>;
}

/** Structural facts about a Java class, resolved once and handed to enrichers as plain data. */
export class ClassFacts {
  constructor(
    readonly qualifiedName: string | null,
    readonly packageName: string | null,
    readonly isInterface: boolean,
    readonly annotations: ReadonlySet<string>,
    readonly hasNoArgCtor: boolean,
    readonly inProjectSource: boolean,
    readonly fileName: string | null,
  ) {}

  hasAnyAnnotation(...simpleNames: string[]): boolean {
    return simpleNames.some((n) => this.annotations.has(n));
  }

  /** True if the type carries any Spring stereotype that makes it visible to component scanning. */
  hasStereotype(): boolean {
    return this.hasAnyAnnotation(
      'Component',
      'Service',
      'Repository',
      'Controller',
      'RestController',
      'Configuration',
      'Mapper',
    );
  }
}

// Port of com.springdebugger.tap.BuildOutputAnalyzer. Decides whether a chunk of build output is
// Spring-specific (generic Java compile errors are the IDE's job) and, if so, classifies it at
// COMPILE phase. The offline pipeline here is rule-engine-only, matching the headless Java path.
import { extract } from './log-extractor';
import { DiagnosisCard } from './models';
import { RuleBasedClassifier } from './rule-based-classifier';
import { RuleCatalog } from './rule-catalog';

const SPRING_MARKERS = [
  'WebSecurityConfigurerAdapter',
  'spring-boot-configuration-processor',
  'lombok',
  'Lombok',
  'mapstruct',
  'MapStruct',
  'MapperImpl',
  'Unmapped target property',
  "Can't map property",
  'No implementation type is registered for return type',
  'Could not generate implementation',
  'UnsupportedClassVersionError',
  'class file has wrong version',
  'no main manifest attribute',
];

export class BuildOutputAnalyzer {
  private readonly classifier: RuleBasedClassifier;

  constructor(catalog: RuleCatalog) {
    this.classifier = new RuleBasedClassifier(catalog);
  }

  analyze(buildOutput: string | null): DiagnosisCard | null {
    if (buildOutput === null || buildOutput.trim() === '') return null;
    if (!isSpringRelated(buildOutput)) return null;
    const signal = extract(buildOutput, 'COMPILE');
    return this.classifier.classify(signal);
  }
}

export function isSpringRelated(text: string): boolean {
  return SPRING_MARKERS.some((m) => text.includes(m));
}

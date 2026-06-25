// Port of com.springdebugger.enricher.PsiEnricher. Resolves the missing bean type against the
// project's actual source (via the EnrichmentContext) and rewrites the diagnosis and fix to name the
// exact type, where it lives, which annotation fits its role, and which bean needed it. This is pure
// decision logic, unit tested with canned ClassFacts; on any uncertainty it returns the card
// unchanged. The fact resolution itself lives in the VS Code EnrichmentContext (M5c).
import { ClassFacts, Enricher, EnrichmentContext } from './enricher';
import { Confidence, DiagnosisCard, RawSignal } from './models';

const QUOTED_TYPE = /'([\w.$]+)'/g;
const OF_TYPE = /of type \[?([\w.$]+)/;

export class PsiEnricher implements Enricher {
  async enrich(card: DiagnosisCard, signal: RawSignal, context: EnrichmentContext): Promise<DiagnosisCard> {
    const ruleId = card.ruleId;
    if (ruleId === null) return card;

    const diRule = ruleId.startsWith('2.') || ruleId === '1.6' || ruleId === '1.7';
    const mapStructRule = ruleId === '13.3' || ruleId === '13.4';
    if (!diRule && !mapStructRule) return card;

    const typeName = this.extractTypeName(signal);
    if (typeName === null) return card;

    const facts = await safeFindClass(context, typeName);
    if (facts === null) return card;

    const fqn = facts.qualifiedName ?? typeName;
    const type = simple(fqn);
    const where =
      facts.fileName ??
      (facts.packageName && facts.packageName !== '' ? 'package ' + facts.packageName : 'the project');
    const neededBy = neededByClause(signal);

    // MapStruct mapper interface
    if (facts.isInterface && facts.hasAnyAnnotation('Mapper')) {
      return rewrite(
        card,
        'HIGH',
        `The MapStruct mapper ${fqn} is annotated @Mapper but its generated implementation is not a Spring bean.${neededBy}`,
        `Add componentModel = "spring" to @Mapper on ${type} (${where}): @Mapper(componentModel = "spring"). MapStruct then registers ${type}Impl as a bean you can inject.`,
      );
    }

    // Third-party / library type: cannot be annotated, must be an @Bean
    if (diRule && !facts.inProjectSource) {
      return rewrite(
        card,
        card.confidence,
        `Spring needs a bean of type ${fqn}, but that type comes from a library, so it has no Spring stereotype and cannot be given one.${neededBy}`,
        `Declare it as an @Bean in a @Configuration class (you cannot annotate library code): @Bean ${type} ${decapitalize(type)}() { return new ${type}(...); }.`,
      );
    }

    // Project interface with no bean: the implementation must be the bean
    if (diRule && facts.isInterface && !facts.hasStereotype()) {
      return rewrite(
        card,
        'HIGH',
        `No bean of type ${fqn} is registered. It is an interface, and Spring registers implementations, not the interface itself.${neededBy}`,
        `Annotate the class that implements ${type} with ${chooseStereotype(type)[0]} (or another stereotype matching its role), or declare an @Bean method returning ${type} in a @Configuration class.`,
      );
    }

    // Project class with no stereotype: add the best-fit one, in its own file
    if (diRule && !facts.hasStereotype()) {
      const ann = chooseStereotype(type);
      return rewrite(
        card,
        'HIGH',
        `The class ${fqn} exists in ${where} but has no Spring stereotype, so component scanning never registers it as a bean.${neededBy}`,
        `Add ${ann[0]} to ${type} in ${where} (${ann[1]}), and keep ${type} inside the package tree under your @SpringBootApplication class so it is scanned.`,
      );
    }

    // Annotated but outside the component-scan tree
    if (diRule && facts.hasStereotype() && (await outsideScanTree(facts, context))) {
      return rewrite(
        card,
        'HIGH',
        `The class ${fqn} is annotated but lives in package ${facts.packageName}, outside the package tree scanned by @SpringBootApplication.${neededBy}`,
        `Move your @SpringBootApplication main class up to a parent package of ${facts.packageName}, or add @ComponentScan("${facts.packageName}") so ${type} is discovered.`,
      );
    }

    return card;
  }

  /** Pulls the bean/type name from the signal: quoted type or "of type X". */
  extractTypeName(signal: RawSignal): string | null {
    const message = signal.deepestCausedByMessage;
    if (message !== null) {
      QUOTED_TYPE.lastIndex = 0;
      let q: RegExpExecArray | null;
      while ((q = QUOTED_TYPE.exec(message)) !== null) {
        if (looksLikeType(q[1])) return q[1];
      }
      const o = OF_TYPE.exec(message);
      if (o && looksLikeType(o[1])) return o[1];
    }
    return null;
  }
}

function rewrite(card: DiagnosisCard, confidence: Confidence, diagnosis: string, fix: string): DiagnosisCard {
  return {
    ruleId: card.ruleId,
    phase: card.phase,
    diagnosisSentence: diagnosis,
    fixSentence: fix,
    confidence,
    excerpt: card.excerpt,
  };
}

/** "@Repository"/"@Service"/"@RestController"/"@Component" plus a short rationale, by name. */
export function chooseStereotype(simpleName: string): [string, string] {
  const n = simpleName.toLowerCase();
  if (n.endsWith('repository') || n.endsWith('dao') || n.endsWith('repo')) {
    return ['@Repository', 'it is a data-access type'];
  }
  if (n.endsWith('service') || n.endsWith('serviceimpl') || n.endsWith('manager')) {
    return ['@Service', 'it holds business logic'];
  }
  if (n.endsWith('controller') || n.endsWith('resource') || n.endsWith('endpoint')) {
    return ['@RestController', 'it handles web requests'];
  }
  return ['@Component', 'it is a general Spring-managed component'];
}

function neededByClause(signal: RawSignal): string {
  const bean = signal.failingBeanName;
  return bean && bean.trim() !== '' ? ` It is required by bean '${bean}'.` : '';
}

async function outsideScanTree(facts: ClassFacts, context: EnrichmentContext): Promise<boolean> {
  const roots = await context.springBootApplicationPackages();
  if (!roots || roots.length === 0) return false;
  const pkg = facts.packageName ?? '';
  for (const root of roots) {
    if (pkg === root || pkg.startsWith(root + '.')) return false;
  }
  return true;
}

async function safeFindClass(context: EnrichmentContext, typeName: string): Promise<ClassFacts | null> {
  try {
    return await context.findClass(typeName);
  } catch {
    return null;
  }
}

function looksLikeType(s: string | null): boolean {
  if (s === null || s === '') return false;
  return s.includes('.') || isUpperCase(s.charAt(0));
}

function isUpperCase(ch: string): boolean {
  return ch !== ch.toLowerCase() && ch === ch.toUpperCase();
}

function simple(qualified: string | null): string {
  if (qualified === null) return 'the type';
  const dot = qualified.lastIndexOf('.');
  return dot >= 0 ? qualified.substring(dot + 1) : qualified;
}

function decapitalize(s: string): string {
  return s === '' ? s : s.charAt(0).toLowerCase() + s.substring(1);
}

// Port of com.springdebugger.convention.CheckRegistry. Maps a checkType string to its
// ConventionCheck implementation.
import { ConventionCheck } from './convention-check';
import { JavadocRequiredCheck } from './checks/javadoc-required';
import { RobotMetadataRequiredCheck } from './checks/robot-metadata-required';
import { RobotTestIdFormatCheck } from './checks/robot-test-id-format';
import { RobotTestCaseDocCheck } from './checks/robot-test-case-doc';
import { RobotTestCaseTagsCheck } from './checks/robot-test-case-tags';
import { FieldInjectionForbiddenCheck } from './checks/field-injection-forbidden';
import { NoSystemOutErrCheck } from './checks/no-system-out-err';
import { OptionalUsageCheck } from './checks/optional-usage';
import { TransactionalMisplacedCheck } from './checks/transactional-misplaced';
import { EntityStringFieldBoundedCheck } from './checks/entity-string-field-bounded';
import { RequestBodyRequiresValidCheck } from './checks/request-body-requires-valid';
import { ServiceClassNamingCheck } from './checks/service-class-naming';
import { ApiVersionPathCheck } from './checks/api-version-path';

const CHECKS: Map<string, ConventionCheck> = new Map();

function register(check: ConventionCheck): void {
  CHECKS.set(check.checkType(), check);
}

register(new JavadocRequiredCheck());
register(new RobotMetadataRequiredCheck());
register(new RobotTestIdFormatCheck());
register(new RobotTestCaseDocCheck());
register(new RobotTestCaseTagsCheck());
register(new FieldInjectionForbiddenCheck());
register(new NoSystemOutErrCheck());
register(new OptionalUsageCheck());
register(new TransactionalMisplacedCheck());
register(new EntityStringFieldBoundedCheck());
register(new RequestBodyRequiresValidCheck());
register(new ServiceClassNamingCheck());
register(new ApiVersionPathCheck());

export function getCheck(checkType: string): ConventionCheck | undefined {
  return CHECKS.get(checkType);
}

export function hasCheck(checkType: string): boolean {
  return CHECKS.has(checkType);
}

export function checkTypes(): string[] {
  return Array.from(CHECKS.keys());
}

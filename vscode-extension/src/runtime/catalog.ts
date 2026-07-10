// Loads the rule catalogs the extension ships with. The YAML is copied into assets/ at package
// time from the single canonical source (see scripts/copy-assets.js). A dev fallback reads the
// canonical repo file so F5 launches work before a bundle step has run.
import * as fs from 'fs';
import * as path from 'path';
import { RuleCatalog } from '../engine';
import { ConventionCatalog } from '../convention/convention-catalog';

export function loadBundledCatalog(extensionPath: string): RuleCatalog {
  const bundled = path.join(extensionPath, 'assets', 'rules', 'spring-boot-rules.yaml');
  if (fs.existsSync(bundled)) {
    return RuleCatalog.fromYaml(fs.readFileSync(bundled, 'utf8'));
  }
  const devSource = path.join(
    extensionPath,
    '..',
    'src',
    'main',
    'resources',
    'rules',
    'spring-boot-rules.yaml',
  );
  return RuleCatalog.fromYaml(fs.readFileSync(devSource, 'utf8'));
}

export function loadBundledConventions(extensionPath: string): ConventionCatalog {
  const bundled = path.join(extensionPath, 'assets', 'rules', 'conventions.yaml');
  if (fs.existsSync(bundled)) {
    return ConventionCatalog.fromYaml(fs.readFileSync(bundled, 'utf8'));
  }
  const devSource = path.join(extensionPath, '..', 'src', 'main', 'resources', 'rules', 'conventions.yaml');
  return ConventionCatalog.fromYaml(fs.readFileSync(devSource, 'utf8'));
}

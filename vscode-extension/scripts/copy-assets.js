// Copies the single-source rule catalog into the extension's bundled assets so it ships inside
// the .vsix. The canonical file lives at ../src/main/resources/rules/spring-boot-rules.yaml and is
// shared with the IntelliJ plugin; it is NEVER forked. This is a copy step, not a second source.
// Tests read the canonical file directly (see test/engine helpers); only packaging needs this copy.
const fs = require('fs');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..', '..');
const src = path.join(repoRoot, 'src', 'main', 'resources', 'rules', 'spring-boot-rules.yaml');
const destDir = path.join(__dirname, '..', 'assets', 'rules');
const dest = path.join(destDir, 'spring-boot-rules.yaml');

if (!fs.existsSync(src)) {
  console.error('Canonical rule catalog not found at ' + src);
  process.exit(1);
}
fs.mkdirSync(destDir, { recursive: true });
fs.copyFileSync(src, dest);
console.log('Copied rule catalog -> ' + path.relative(process.cwd(), dest));

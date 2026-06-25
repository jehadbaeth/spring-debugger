import * as path from 'path';
import { defineConfig } from 'vitest/config';

// Alias the vscode module to a controllable stub so the extension glue can run under vitest with no
// extension host. Engine/pure tests never import vscode and are unaffected.
export default defineConfig({
  resolve: {
    alias: {
      vscode: path.resolve(process.cwd(), 'test/vscode-stub.ts'),
    },
  },
  test: {
    environment: 'node',
  },
});

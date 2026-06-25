// A controllable fake of the vscode module, aliased in for unit tests (vitest.config.ts). It lets
// tests drive activate(), run registered commands, flip configuration, and assert on notifications,
// the webview, and the tree, without a real extension host. The `control` object is the test seam.

type Listener = (e: { affectsConfiguration: (section: string) => boolean }) => void;

interface ShownMessage {
  kind: 'info' | 'warning' | 'error';
  text: string;
  actions: string[];
}

class EventEmitter<T> {
  private handlers: Array<(e: T) => void> = [];
  event = (h: (e: T) => void) => {
    this.handlers.push(h);
    return { dispose: () => {} };
  };
  fire(e: T): void {
    for (const h of this.handlers) h(e);
  }
  dispose(): void {}
}

class TreeItem {
  description?: string;
  tooltip?: unknown;
  iconPath?: unknown;
  command?: unknown;
  constructor(public label: string, public collapsibleState?: number) {}
}

class ThemeIcon {
  constructor(public id: string) {}
}

class MarkdownString {
  value = '';
  appendMarkdown(s: string): this {
    this.value += s;
    return this;
  }
}

export const control = {
  commands: new Map<string, (...args: unknown[]) => unknown>(),
  configStore: {} as Record<string, unknown>,
  configListeners: [] as Listener[],
  workspaceFolders: undefined as { uri: { fsPath: string } }[] | undefined,
  messages: [] as ShownMessage[],
  clipboard: '',
  inputBoxAnswer: undefined as string | undefined,
  warningResponse: undefined as string | undefined,
  treeProviders: new Map<string, unknown>(),
  statusBar: undefined as undefined | { text: string; tooltip?: string; shown: boolean },
  panels: [] as Array<{ html: string; revealed: boolean; disposed: boolean }>,
  reset(): void {
    this.commands.clear();
    this.configStore = {};
    this.configListeners = [];
    this.workspaceFolders = undefined;
    this.messages = [];
    this.clipboard = '';
    this.inputBoxAnswer = undefined;
    this.warningResponse = undefined;
    this.treeProviders.clear();
    this.statusBar = undefined;
    this.panels = [];
  },
  async run(commandId: string, ...args: unknown[]): Promise<unknown> {
    const cb = this.commands.get(commandId);
    if (!cb) throw new Error('command not registered: ' + commandId);
    return cb(...args);
  },
  fireConfigChange(): void {
    const e = { affectsConfiguration: () => true };
    for (const l of this.configListeners) l(e);
  },
};

export const StatusBarAlignment = { Left: 1, Right: 2 };
export const ViewColumn = { Active: -1, Beside: -2, One: 1 };
export const TreeItemCollapsibleState = { None: 0, Collapsed: 1, Expanded: 2 };
export const SymbolKind = { Class: 4, Interface: 10 };

export const window = {
  createStatusBarItem() {
    const item = {
      text: '',
      tooltip: undefined as string | undefined,
      command: undefined as unknown,
      shown: false,
      show() {
        this.shown = true;
      },
      hide() {
        this.shown = false;
      },
      dispose() {},
    };
    control.statusBar = item;
    return item;
  },
  registerTreeDataProvider(id: string, provider: unknown) {
    control.treeProviders.set(id, provider);
    return { dispose: () => {} };
  },
  createWebviewPanel() {
    const panel = {
      html: '',
      revealed: false,
      disposed: false,
      webview: {
        get html() {
          return panel.html;
        },
        set html(v: string) {
          panel.html = v;
        },
        onDidReceiveMessage: () => ({ dispose: () => {} }),
      },
      onDidDispose: () => ({ dispose: () => {} }),
      reveal() {
        panel.revealed = true;
      },
      dispose() {
        panel.disposed = true;
      },
    };
    control.panels.push(panel);
    return panel;
  },
  async showInputBox() {
    return control.inputBoxAnswer;
  },
  async showInformationMessage(text: string, ...actions: string[]) {
    control.messages.push({ kind: 'info', text, actions });
    return undefined;
  },
  async showWarningMessage(text: string, ...actions: string[]) {
    control.messages.push({ kind: 'warning', text, actions });
    return control.warningResponse;
  },
  async showErrorMessage(text: string, ...actions: string[]) {
    control.messages.push({ kind: 'error', text, actions });
    return undefined;
  },
  setStatusBarMessage() {
    return { dispose: () => {} };
  },
};

export const commands = {
  registerCommand(id: string, cb: (...args: unknown[]) => unknown) {
    control.commands.set(id, cb);
    return { dispose: () => control.commands.delete(id) };
  },
  // The enrichment context calls this; default to no symbols so enrichment is a graceful no-op.
  async executeCommand() {
    return [];
  },
};

export const workspace = {
  get workspaceFolders() {
    return control.workspaceFolders;
  },
  getConfiguration() {
    return {
      get<T>(key: string, def: T): T {
        const v = control.configStore[key];
        return (v === undefined ? def : v) as T;
      },
    };
  },
  onDidChangeConfiguration(l: Listener) {
    control.configListeners.push(l);
    return { dispose: () => {} };
  },
  async findFiles() {
    return [];
  },
  async openTextDocument() {
    return { getText: () => '' };
  },
};

export const env = {
  clipboard: {
    async readText() {
      return control.clipboard;
    },
    async writeText(v: string) {
      control.clipboard = v;
    },
  },
};

export { EventEmitter, TreeItem, ThemeIcon, MarkdownString };

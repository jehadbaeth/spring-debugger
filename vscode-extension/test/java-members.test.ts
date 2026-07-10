import { describe, it, expect } from 'vitest';
import { parseJavaFile } from '../src/convention/java-members';
import { maskJava } from '../src/convention/mask-java';

function parse(src: string) {
  return parseJavaFile(src, maskJava(src));
}

describe('java-members', () => {
  it('finds a field with an injection annotation', () => {
    const src = `package x;\nclass Foo {\n  @Autowired\n  private OrderRepository repo;\n}\n`;
    const f = parse(src);
    expect(f.fields).toHaveLength(1);
    expect(f.fields[0].name).toBe('repo');
    expect(f.fields[0].type).toBe('OrderRepository');
    expect(f.fields[0].annotations.map((a) => a.name)).toContain('Autowired');
  });

  it('does not treat a local variable as a field', () => {
    const src = `class Foo {\n  void run() {\n    String value = compute();\n    int x = 5;\n  }\n}\n`;
    const f = parse(src);
    expect(f.fields).toHaveLength(0);
  });

  it('finds a method and its parameters with annotations', () => {
    const src =
      `class Foo {\n  @PostMapping\n  public ResponseEntity<Void> create(@RequestBody UserDto dto, @PathVariable Long id) {\n    return null;\n  }\n}\n`;
    const f = parse(src);
    expect(f.methods).toHaveLength(1);
    const m = f.methods[0];
    expect(m.name).toBe('create');
    expect(m.annotations.map((a) => a.name)).toContain('PostMapping');
    expect(m.parameters).toHaveLength(2);
    expect(m.parameters[0].name).toBe('dto');
    expect(m.parameters[0].annotations.map((a) => a.name)).toContain('RequestBody');
    expect(m.parameters[1].name).toBe('id');
    expect(m.parameters[1].annotations.map((a) => a.name)).toContain('PathVariable');
  });

  it('does not match control flow statements as methods', () => {
    const src = `class Foo {\n  void run() {\n    if (x > 0) {\n      return doThing(x);\n    } else if (y > 0) {\n      return other();\n    }\n  }\n}\n`;
    const f = parse(src);
    expect(f.methods.map((m) => m.name)).toEqual(['run']);
  });

  it('detects a constructor is not counted as a method', () => {
    const src = `class Foo {\n  public Foo(Bar bar) {\n  }\n  public void doIt() {}\n}\n`;
    const f = parse(src);
    expect(f.methods.map((m) => m.name)).toEqual(['doIt']);
  });

  it('detects javadoc presence on a method', () => {
    const src = `class Foo {\n  /**\n   * Does the thing.\n   */\n  public void doIt() {}\n\n  public void noDoc() {}\n}\n`;
    const f = parse(src);
    const doIt = f.methods.find((m) => m.name === 'doIt')!;
    const noDoc = f.methods.find((m) => m.name === 'noDoc')!;
    expect(doIt.hasJavadoc).toBe(true);
    expect(noDoc.hasJavadoc).toBe(false);
  });

  it('associates fields and methods with the enclosing @Entity class', () => {
    const src =
      `class Other {\n  private String x;\n}\n\n@Entity\nclass Order {\n  private String note;\n}\n`;
    const f = parse(src);
    const orderClass = f.classes.find((c) => c.name === 'Order')!;
    expect(orderClass.annotations.map((a) => a.name)).toContain('Entity');
    const noteField = f.fields.find((fld) => fld.name === 'note')!;
    expect(f.classes[noteField.enclosingClassIndex].name).toBe('Order');
  });

  it('reads a literal path attribute off a class-level annotation', () => {
    const src = `@RequestMapping("/api/v1/orders")\nclass OrderController {\n}\n`;
    const f = parse(src);
    const c = f.classes[0];
    const mapping = c.annotations.find((a) => a.name === 'RequestMapping')!;
    expect(mapping.args).toBe('"/api/v1/orders"');
  });
});

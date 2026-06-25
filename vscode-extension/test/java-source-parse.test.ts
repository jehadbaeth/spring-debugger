import { describe, it, expect } from 'vitest';
import { buildClassFacts, packageOf } from '../src/runtime/java-source-parse';

describe('java-source-parse', () => {
  it('reads the package', () => {
    expect(packageOf('package com.example.web;\n\nclass Foo {}')).toBe('com.example.web');
    expect(packageOf('class Foo {}')).toBeNull();
  });

  it('builds facts for an annotated service class', () => {
    const src = `package com.example;\n\nimport org.springframework.stereotype.Service;\n\n@Service\npublic class OrderService {\n}\n`;
    const f = buildClassFacts(src, 'OrderService', 'OrderService.java')!;
    expect(f.qualifiedName).toBe('com.example.OrderService');
    expect(f.packageName).toBe('com.example');
    expect(f.isInterface).toBe(false);
    expect(f.hasStereotype()).toBe(true);
    expect(f.hasAnyAnnotation('Service')).toBe(true);
    expect(f.inProjectSource).toBe(true);
    expect(f.fileName).toBe('OrderService.java');
  });

  it('detects an interface with no stereotype', () => {
    const src = `package com.example;\n\npublic interface PaymentGateway {\n  void pay();\n}\n`;
    const f = buildClassFacts(src, 'PaymentGateway', 'PaymentGateway.java')!;
    expect(f.isInterface).toBe(true);
    expect(f.hasStereotype()).toBe(false);
  });

  it('reads a fully qualified annotation and a MapStruct @Mapper', () => {
    const src = `package com.example;\n\n@org.mapstruct.Mapper\npublic interface UserMapper {}\n`;
    const f = buildClassFacts(src, 'UserMapper', 'UserMapper.java')!;
    expect(f.isInterface).toBe(true);
    expect(f.hasAnyAnnotation('Mapper')).toBe(true);
  });

  it('collects multiple annotations on the type', () => {
    const src = `package com.example;\n\n@Service\n@Validated\npublic class Thing {}\n`;
    const f = buildClassFacts(src, 'Thing', 'Thing.java')!;
    expect(f.hasAnyAnnotation('Service')).toBe(true);
    expect(f.hasAnyAnnotation('Validated')).toBe(true);
  });

  it('does not attribute a previous type\'s annotation to this one', () => {
    const src = `package com.example;\n\n@Service\nclass First {}\n\nclass Second {}\n`;
    const second = buildClassFacts(src, 'Second', 'x.java')!;
    expect(second.hasAnyAnnotation('Service')).toBe(false);
    const first = buildClassFacts(src, 'First', 'x.java')!;
    expect(first.hasAnyAnnotation('Service')).toBe(true);
  });

  it('returns null when the type is not declared in the file', () => {
    expect(buildClassFacts('package com.example;\nclass Foo {}', 'Bar', 'x.java')).toBeNull();
  });
});

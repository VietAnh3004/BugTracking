import { describe, expect, it } from 'vitest';

import { highlightLines, languageOf } from '@/features/snapshots/lib/highlight';

describe('languageOf', () => {
  it('nhận ngôn ngữ theo phần mở rộng, không phân biệt hoa thường', () => {
    expect(languageOf('src/main/java/App.java')).toBe('java');
    expect(languageOf('pom.xml')).toBe('markup');
    expect(languageOf('application.YML')).toBe('yaml');
    expect(languageOf('infra/Dockerfile')).toBe('docker');
  });

  it('không biết ngôn ngữ thì trả null — hiện chữ trơn, không đoán', () => {
    expect(languageOf('README')).toBeNull();
    expect(languageOf('notes.txt')).toBeNull();
  });
});

describe('highlightLines', () => {
  const javaFile = [
    'package vn.hust;',
    '',
    '/**',
    ' * Mo ta "co dau nhay" va <b>the</b>',
    ' */',
    'public class A {',
    '    String s = "a\\nb";',
    '}',
    '',
  ];

  /**
   * Bất biến quan trọng nhất: tô màu chỉ THÊM màu. Nối các đoạn của mỗi dòng phải ra đúng dòng
   * gốc — mất hay đổi một ký tự là người đọc thấy mã nguồn sai, đúng ở chỗ họ đang soi bằng chứng.
   */
  it('nối các đoạn của mỗi dòng ra ĐÚNG dòng gốc, kể cả dòng rỗng ở cuối', () => {
    const result = highlightLines(javaFile, 'java');

    expect(result).toHaveLength(javaFile.length);
    expect(result.map((segments) => segments.map((s) => s.text).join(''))).toEqual(javaFile);
  });

  it('dòng giữa comment khối được gán loại comment', () => {
    const result = highlightLines(javaFile, 'java');

    expect(result[3]?.every((segment) => segment.types.includes('comment'))).toBe(true);
  });

  it('ngôn ngữ null trả mỗi dòng một đoạn chữ trơn', () => {
    expect(highlightLines(['a < b', 'c'], null)).toEqual([
      [{ text: 'a < b', types: [] }],
      [{ text: 'c', types: [] }],
    ]);
  });
});

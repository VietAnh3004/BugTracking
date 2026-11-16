/**
 * Tô màu cú pháp cho khung xem mã — trả về CÁC ĐOẠN CHỮ kèm loại token, KHÔNG trả HTML.
 *
 * ========================== RÀNG BUỘC BẢO MẬT (ADR 0006) ==========================
 * Thư viện tô màu phổ biến (highlight.js, Prism bản gốc, shiki `codeToHtml`) trả CHUỖI HTML, và
 * dùng được chúng nghĩa là phải `dangerouslySetInnerHTML` lên nội dung file người dùng upload —
 * đúng bề mặt XSS mà `CodeLine` cấm. `refractor` dùng cùng bộ ngữ pháp Prism nhưng trả CÂY CÚ
 * PHÁP (hast); ở đây cây đó được làm phẳng thành mảng `{ text, types }`, và React render `text`
 * thành text node — tự escape. Không có chỗ nào chuỗi từ file người dùng được hiểu là HTML.
 * ===============================================================================
 *
 * Vì sao tô màu CẢ FILE rồi mới chia dòng, không tô từng dòng: comment khối `/** … *\/` và chuỗi
 * nhiều dòng trải qua nhiều dòng. Tô từng dòng độc lập thì dòng giữa comment bị hiểu là code và
 * tô sai màu — vulnshop có Javadoc ở gần như mọi method nên lỗi này sẽ hiện khắp nơi.
 *
 * Chỉ nạp vài ngôn ngữ thật sự gặp trong repo Java (`refractor/core` + từng ngữ pháp), không nạp
 * cả bộ ~300 ngôn ngữ của `refractor` mặc định.
 */

import { refractor } from 'refractor/core';
import bash from 'refractor/bash';
import css from 'refractor/css';
import docker from 'refractor/docker';
import java from 'refractor/java';
import javascript from 'refractor/javascript';
import json from 'refractor/json';
import markup from 'refractor/markup';
import properties from 'refractor/properties';
import python from 'refractor/python';
import sql from 'refractor/sql';
import typescript from 'refractor/typescript';
import yaml from 'refractor/yaml';

import type { Element, Root, RootContent } from 'hast';

for (const grammar of [
  markup,
  css,
  javascript,
  typescript,
  java,
  json,
  yaml,
  properties,
  sql,
  python,
  bash,
  docker,
]) {
  refractor.register(grammar);
}

/** Một đoạn chữ liền mạch cùng loại token. `types` rỗng = chữ thường, không tô. */
export interface Segment {
  text: string;
  types: string[];
}

const LANGUAGE_BY_EXTENSION: Record<string, string> = {
  java: 'java',
  xml: 'markup',
  html: 'markup',
  htm: 'markup',
  svg: 'markup',
  css: 'css',
  js: 'javascript',
  mjs: 'javascript',
  cjs: 'javascript',
  jsx: 'javascript',
  ts: 'typescript',
  tsx: 'typescript',
  json: 'json',
  yml: 'yaml',
  yaml: 'yaml',
  properties: 'properties',
  sql: 'sql',
  py: 'python',
  sh: 'bash',
};

/** Ngôn ngữ theo tên file; `null` nghĩa là không tô (hiện chữ trơn). */
export function languageOf(path: string): string | null {
  const fileName = path.slice(path.lastIndexOf('/') + 1);
  if (fileName === 'Dockerfile') {
    return 'docker';
  }
  const dot = fileName.lastIndexOf('.');
  if (dot === -1) {
    return null;
  }
  return LANGUAGE_BY_EXTENSION[fileName.slice(dot + 1).toLowerCase()] ?? null;
}

/**
 * Tô màu các dòng rồi trả về đúng SỐ DÒNG đó, mỗi dòng là một mảng đoạn.
 *
 * Bất biến quan trọng nhất, có test canh: nối `text` của mọi đoạn trong dòng i phải ra ĐÚNG
 * `lines[i]`. Tô màu chỉ được thêm màu, không bao giờ được thêm/bớt/đổi một ký tự mã nguồn.
 */
export function highlightLines(lines: string[], language: string | null): Segment[][] {
  if (language === null || !refractor.registered(language)) {
    return lines.map((line) => [{ text: line, types: [] }]);
  }
  const tree = refractor.highlight(lines.join('\n'), language);
  const flat: Segment[] = [];
  flatten(tree, [], flat);
  return splitIntoLines(flat, lines.length);
}

function flatten(node: Root | RootContent, inherited: string[], out: Segment[]): void {
  if (node.type === 'text') {
    out.push({ text: node.value, types: inherited });
    return;
  }
  if (node.type === 'root' || node.type === 'element') {
    const types = node.type === 'element' ? [...inherited, ...tokenTypesOf(node)] : inherited;
    for (const child of node.children) {
      flatten(child, types, out);
    }
  }
}

/** Prism gắn class dạng `token keyword`; bỏ chữ `token` chung, giữ loại cụ thể. */
function tokenTypesOf(element: Element): string[] {
  const className = element.properties['className'];
  if (!Array.isArray(className)) {
    return [];
  }
  return className.map(String).filter((name) => name !== 'token');
}

function splitIntoLines(segments: Segment[], lineCount: number): Segment[][] {
  let current: Segment[] = [];
  const result: Segment[][] = [current];
  for (const segment of segments) {
    const parts = segment.text.split('\n');
    parts.forEach((part, index) => {
      if (index > 0) {
        current = [];
        result.push(current);
      }
      if (part !== '') {
        current.push({ text: part, types: segment.types });
      }
    });
  }
  // Dòng rỗng ở cuối (file kết thúc bằng các dòng trống) không sinh đoạn nào — bù cho đủ số dòng
  // để chỉ số dòng không lệch.
  while (result.length < lineCount) {
    result.push([]);
  }
  return result.slice(0, lineCount);
}

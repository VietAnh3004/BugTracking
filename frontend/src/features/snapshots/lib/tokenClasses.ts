/**
 * Màu theo loại token Prism. TÊN CLASS NGUYÊN VĂN, không ghép chuỗi (`text-${type}`): Tailwind
 * quét mã nguồn bằng phân tích tĩnh, class ghép động không bao giờ được sinh ra (xem
 * `src/index.css`).
 *
 * Bảng màu nhạt, gần với GitHub: khung xem mã là để ĐỌC bằng chứng, dải vàng tô vùng cảnh báo
 * phải vẫn là thứ nổi nhất trên màn hình — màu cú pháp đậm sẽ tranh mất sự chú ý đó.
 */
const TOKEN_CLASSES: Record<string, string> = {
  comment: 'text-slate-500 italic',
  prolog: 'text-slate-500',
  doctype: 'text-slate-500',
  cdata: 'text-slate-500',
  keyword: 'text-rose-700',
  boolean: 'text-blue-700',
  number: 'text-blue-700',
  constant: 'text-blue-700',
  string: 'text-emerald-800',
  char: 'text-emerald-800',
  'attr-value': 'text-emerald-800',
  regex: 'text-emerald-800',
  'class-name': 'text-amber-800',
  function: 'text-violet-700',
  annotation: 'text-cyan-700',
  builtin: 'text-cyan-700',
  tag: 'text-rose-700',
  'attr-name': 'text-violet-700',
  property: 'text-blue-800',
  key: 'text-blue-800',
  namespace: 'text-slate-600',
  operator: 'text-slate-600',
  punctuation: 'text-slate-500',
};

/** Class cho một đoạn: lấy loại token CỤ THỂ nhất (cuối danh sách) có trong bảng. */
export function classForTokenTypes(types: string[]): string | undefined {
  for (const type of [...types].reverse()) {
    const cls = TOKEN_CLASSES[type];
    if (cls !== undefined) {
      return cls;
    }
  }
  return undefined;
}

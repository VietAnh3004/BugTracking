/**
 * Một dòng mã nguồn: số dòng + nội dung.
 *
 * ========================== QUY TẮC CỨNG VỀ BẢO MẬT ==========================
 * TUYỆT ĐỐI KHÔNG dùng `dangerouslySetInnerHTML` cho bất kỳ dữ liệu nào bắt nguồn từ file người
 * dùng upload. Đây là bề mặt XSS thật của dự án, không phải rủi ro lý thuyết: một repo độc hại
 * chứa file `.html`/`.js` với payload là kịch bản hoàn toàn hiện thực trong chính luồng nghiệp vụ.
 * React tự escape khi render text node, nên chỉ cần không mở cửa bằng `dangerouslySetInnerHTML` là
 * bề mặt này đóng. Xem ADR 0006, mục "Bề mặt XSS thật của dự án này".
 *
 * Prop `render` dưới đây là đường ráp cho tuần 10-11 (tô màu vùng bằng chứng): nó phải trả về CÂY
 * REACT ELEMENT, không phải chuỗi HTML. Thư viện highlight nào chỉ sinh ra chuỗi HTML đều bị loại
 * vì dùng được nó bắt buộc phải phá quy tắc trên.
 * =============================================================================
 */

import type { ReactNode } from 'react';

interface CodeLineProps {
  lineNumber: number;
  text: string;
  highlighted: boolean;
  render?: (text: string) => ReactNode;
}

export function CodeLine({ lineNumber, text, highlighted, render }: CodeLineProps) {
  return (
    <div id={`L${String(lineNumber)}`} className={`flex ${highlighted ? 'bg-line-highlight' : ''}`}>
      {/*
        `select-none` là bắt buộc, không phải trang trí: thiếu nó thì bôi đen nhiều dòng để copy sẽ
        dính cả số dòng vào clipboard — ai cũng thấy ngay khi demo. `tabular-nums` giữ các chữ số
        cùng bề rộng để cột số dòng không nhấp nháy khi cuộn.
      */}
      <span className="text-ink-muted w-12 shrink-0 pr-3 text-right tabular-nums select-none">
        {lineNumber}
      </span>
      {/* `whitespace-pre` giữ nguyên thụt lề của mã nguồn; `break-words` để dòng dài không tràn ngang. */}
      <code className="min-w-0 flex-1 whitespace-pre">
        {render === undefined ? text : render(text)}
      </code>
    </div>
  );
}

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
 * Tô màu cú pháp đi qua prop `segments` — các đoạn CHỮ kèm loại token do `lib/highlight.ts` tách
 * sẵn, render thành `<span>` chứa text node. Thư viện highlight nào chỉ sinh ra chuỗi HTML đều bị
 * loại vì dùng được nó bắt buộc phải phá quy tắc trên.
 * =============================================================================
 */

import { classForTokenTypes } from '@/features/snapshots/lib/tokenClasses';

import type { Segment } from '@/features/snapshots/lib/highlight';

interface CodeLineProps {
  lineNumber: number;
  text: string;
  highlighted: boolean;
  /** Các đoạn đã tô màu của chính dòng này; vắng thì hiện chữ trơn. */
  segments?: Segment[];
}

export function CodeLine({ lineNumber, text, highlighted, segments }: CodeLineProps) {
  return (
    <div
      id={`L${String(lineNumber)}`}
      className={`flex scroll-mt-24 ${highlighted ? 'bg-line-highlight shadow-[inset_2px_0_0_var(--color-warning)]' : ''}`}
    >
      {/*
        `select-none` là bắt buộc, không phải trang trí: thiếu nó thì bôi đen nhiều dòng để copy sẽ
        dính cả số dòng vào clipboard — ai cũng thấy ngay khi demo. `tabular-nums` giữ các chữ số
        cùng bề rộng để cột số dòng không nhấp nháy khi cuộn.
      */}
      <span
        className={`bg-code-gutter border-border mr-4 w-14 shrink-0 border-r pr-3 text-right select-none ${highlighted ? 'text-ink' : 'text-ink-subtle'}`}
      >
        {lineNumber}
      </span>
      {/* `whitespace-pre` giữ nguyên thụt lề của mã nguồn; `break-words` để dòng dài không tràn ngang. */}
      <code className="text-ink min-w-0 flex-1 pr-4 whitespace-pre">
        {segments === undefined
          ? text
          : segments.map((segment, index) => (
              // Chỉ số làm khoá là đúng ở đây: các đoạn của một dòng không bao giờ đổi thứ tự.
              <span key={index} className={classForTokenTypes(segment.types)}>
                {segment.text}
              </span>
            ))}
      </code>
    </div>
  );
}

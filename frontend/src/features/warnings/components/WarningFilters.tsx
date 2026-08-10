/**
 * Bộ lọc danh sách cảnh báo. Thuần trình bày: không gọi API, không giữ trạng thái — trang chủ
 * quản lý giá trị và truyền xuống, nhờ vậy bộ lọc dùng lại được ở màn hình khác (vd chi tiết một
 * file) mà không kéo theo logic nạp dữ liệu.
 *
 * CỐ Ý không có ô chọn "sắp xếp theo": thứ tự đã cố định theo `warning.severity_rank` ở tầng SQL.
 * Thêm ô đó là mời người dùng sắp theo `severityNormalized` — chuỗi — và nhận về HIGH, INFO, LOW,
 * MEDIUM.
 */

import type {
  WarningFilters as Filters,
  WarningSeverity,
  WarningTool,
} from '@/features/warnings/api/warningApi';

const TOOLS: WarningTool[] = ['spotbugs', 'semgrep'];
const SEVERITIES: WarningSeverity[] = ['HIGH', 'MEDIUM', 'LOW', 'INFO'];

interface WarningFiltersProps {
  value: Filters;
  onChange: (next: Filters) => void;
}

export function WarningFilters({ value, onChange }: WarningFiltersProps) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <label className="flex flex-col gap-1 text-sm">
        <span className="text-ink-muted">Công cụ</span>
        <select
          className="border-border rounded border bg-white px-2 py-1"
          value={value.tool ?? ''}
          onChange={(event) => {
            const next = event.target.value;
            onChange({ ...value, tool: next === '' ? undefined : (next as WarningTool) });
          }}
        >
          <option value="">Tất cả</option>
          {TOOLS.map((tool) => (
            <option key={tool} value={tool}>
              {tool}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="text-ink-muted">Mức nghiêm trọng</span>
        <select
          className="border-border rounded border bg-white px-2 py-1"
          value={value.severity ?? ''}
          onChange={(event) => {
            const next = event.target.value;
            onChange({ ...value, severity: next === '' ? undefined : (next as WarningSeverity) });
          }}
        >
          <option value="">Tất cả</option>
          {SEVERITIES.map((severity) => (
            <option key={severity} value={severity}>
              {severity}
            </option>
          ))}
        </select>
      </label>

      {/*
       * Nhãn nói rõ "một phần": backend khớp theo chuỗi con, không phân biệt hoa thường. Bản đầu
       * ghi mỗi "Đường dẫn file" với placeholder `src/main/java/…` nên người dùng tưởng phải gõ
       * đủ đường dẫn — mà backend lúc đó CŨNG so khớp bằng chính xác, nên gõ thiếu một ký tự là
       * bảng rỗng và không có gì cho biết vì sao.
       */}
      <label className="flex min-w-0 flex-1 flex-col gap-1 text-sm">
        <span className="text-ink-muted">Một phần đường dẫn file</span>
        <input
          type="text"
          className="border-border rounded border bg-white px-2 py-1"
          placeholder="UserService  ·  vulnshop/user  ·  .java"
          value={value.filePath ?? ''}
          onChange={(event) => {
            const next = event.target.value;
            onChange({ ...value, filePath: next === '' ? undefined : next });
          }}
        />
      </label>
    </div>
  );
}

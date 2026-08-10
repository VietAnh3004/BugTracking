import type { ReactNode } from 'react';

import { CHUA_CO } from '@/features/review/lib/formatMetric';

/** Một nhóm chỉ số. `note` để nói rõ chỉ số nghĩa là gì — người chấm không đoán được từ nhãn. */
export function MetricCard({
  title,
  note,
  children,
}: {
  title: string;
  note?: string;
  children: ReactNode;
}) {
  return (
    <section className="border-border rounded-md border p-4">
      <h2 className="text-ink m-0 text-sm font-semibold">{title}</h2>
      {note === undefined ? null : <p className="text-ink-muted mt-1 mb-0 text-xs">{note}</p>}
      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2">{children}</dl>
    </section>
  );
}

/**
 * Một dòng nhãn ↔ giá trị.
 *
 * Giá trị đã được định dạng sẵn ở tầng gọi (`formatRatio`/`formatCount`) chứ component này không
 * tự tính: chỗ duy nhất biết "chưa có dữ liệu" phải là một chỗ, xem `formatMetric.ts`.
 */
export function Metric({ label, value, hint }: { label: string; value: string; hint?: string }) {
  const missing = value === CHUA_CO;
  return (
    <div>
      <dt className="text-ink-muted text-xs" title={hint}>
        {label}
      </dt>
      <dd
        className={`m-0 text-lg font-semibold ${missing ? 'text-ink-muted font-normal' : ''}`}
        // Dấu gạch một mình không nói được gì cho người dùng trình đọc màn hình.
        aria-label={missing ? `${label}: chưa đủ dữ liệu để tính` : undefined}
      >
        {value}
      </dd>
    </div>
  );
}

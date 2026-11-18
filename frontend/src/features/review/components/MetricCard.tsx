import type { ReactNode } from 'react';

import { CHUA_CO } from '@/features/review/lib/formatMetric';
import { InfoTip } from '@/shared/ui/InfoTip';
import { CARD } from '@/shared/ui/styles';

/**
 * Một nhóm chỉ số. `note` để nói rõ nhóm này đo gì — người chấm không đoán được từ nhãn. Hiện qua
 * nút "?" cạnh tiêu đề chứ không thành dòng chữ luôn hiện: dashboard nhiều thẻ, để hết thì rối.
 */
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
    <section className={`${CARD} p-5`}>
      <div className="flex items-center gap-1.5">
        <h2 className="text-ink m-0 text-base font-semibold">{title}</h2>
        {note === undefined ? null : <InfoTip label={`Giải thích: ${title}`}>{note}</InfoTip>}
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">{children}</dl>
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
    <div className="bg-surface-muted rounded-lg px-3 py-2.5">
      <dt className="text-ink-muted flex items-center gap-1 text-xs">
        {label}
        {hint === undefined ? null : <InfoTip label={`Giải thích: ${label}`}>{hint}</InfoTip>}
      </dt>
      <dd
        className={`m-0 mt-0.5 text-xl tracking-tight tabular-nums ${missing ? 'text-ink-subtle font-normal' : 'text-ink font-semibold'}`}
        // Dấu gạch một mình không nói được gì cho người dùng trình đọc màn hình.
        aria-label={missing ? `${label}: chưa đủ dữ liệu để tính` : undefined}
      >
        {value}
      </dd>
    </div>
  );
}

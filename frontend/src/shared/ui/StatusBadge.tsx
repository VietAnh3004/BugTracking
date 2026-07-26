/**
 * Nhãn trạng thái.
 *
 * Thuần trình bày: không gọi API, không biết gì về cảnh báo hay bằng chứng. Nhờ vậy nó dùng lại
 * được cho trạng thái service, verdict của agent và kết quả human review.
 */

export type StatusTone = 'ok' | 'warning' | 'danger' | 'neutral';

const TONE_COLORS: Record<StatusTone, { background: string; color: string }> = {
  ok: { background: '#dcfce7', color: '#166534' },
  warning: { background: '#fef9c3', color: '#854d0e' },
  danger: { background: '#fee2e2', color: '#991b1b' },
  neutral: { background: '#e5e7eb', color: '#374151' },
};

interface StatusBadgeProps {
  tone: StatusTone;
  label: string;
}

export function StatusBadge({ tone, label }: StatusBadgeProps) {
  return (
    <span
      style={{
        ...TONE_COLORS[tone],
        borderRadius: '9999px',
        fontSize: '0.8125rem',
        fontWeight: 600,
        padding: '0.125rem 0.625rem',
      }}
    >
      {label}
    </span>
  );
}

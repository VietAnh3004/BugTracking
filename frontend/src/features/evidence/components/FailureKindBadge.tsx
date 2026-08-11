import type { components } from '@/shared/lib/api-types';

type FailureKind = NonNullable<components['schemas']['TestCaseSummaryView']['failureKind']>;

/**
 * `<error>` hay `<failure>` — hai thẻ XML của Surefire, và sự khác nhau KHÔNG phải chi tiết kỹ thuật.
 *
 * `ERROR` là ngoại lệ thoát ra khỏi test, thường ném từ **mã production**, nên stack trace của nó
 * có frame trỏ về đúng dòng bị cảnh báo — đó là loại bằng chứng thuyết phục nhất cho một liên kết
 * `SUPPORTS`. `FAILURE` là assertion thất bại, ném từ chính lớp test, nên trace dừng ở lớp test và
 * không nói được gì về mã production.
 *
 * Trên vulnshop chỉ 3/6 test hỏng cho frame trỏ về mã production — đúng vì chỉ nhóm `ERROR` mới
 * làm được. Giấu phân biệt này đi thì người đọc thấy "FAILED" như nhau và không biết vì sao liên
 * kết này mạnh hơn liên kết kia. Xem `TestFailureKind` ở backend.
 */
const STYLES: Record<FailureKind, { label: string; hint: string; className: string }> = {
  ERROR: {
    label: 'ERROR',
    hint: 'Ngoại lệ thoát ra khỏi test — thường ném từ mã production, nên stack trace trỏ về đúng dòng bị cảnh báo',
    className: 'bg-red-100 text-red-800',
  },
  FAILURE: {
    label: 'FAILURE',
    hint: 'Assertion thất bại — ném từ chính lớp test, nên stack trace không trỏ về mã production',
    className: 'bg-amber-100 text-amber-800',
  },
};

export function FailureKindBadge({ kind }: { kind: FailureKind }) {
  const style = STYLES[kind];
  return (
    <span
      title={style.hint}
      className={`inline-flex rounded px-1.5 py-0.5 font-mono text-[11px] font-medium ${style.className}`}
    >
      {style.label}
    </span>
  );
}

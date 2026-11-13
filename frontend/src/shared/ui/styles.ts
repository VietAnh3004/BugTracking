/**
 * Lớp CSS dùng chung cho nút, thẻ và ô nhập — một chỗ duy nhất quyết định "nút chính trông thế
 * nào", thay vì mỗi component tự chép một chuỗi class dài rồi lệch nhau dần.
 *
 * Là HẰNG CHUỖI NGUYÊN VĂN chứ không ghép động (`bg-${variant}`): Tailwind quét mã nguồn bằng
 * phân tích tĩnh, class ghép chuỗi không bao giờ được sinh ra (xem `src/index.css`). Ghép hai hằng
 * nguyên văn với nhau bằng dấu cách thì an toàn — mỗi class vẫn hiện nguyên hình trong file này.
 *
 * Chọn hằng thay vì component `<Button>`: nút trong ứng dụng là `<button>`, `<Link>` lẫn `<label>`
 * (nút chọn file), một component bọc cả ba sẽ phải tự làm lại phần polymorphic mà lớp CSS cho
 * không mất gì.
 */

const BUTTON_BASE =
  'inline-flex cursor-pointer items-center justify-center gap-1.5 rounded-lg font-semibold whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-55';

const BUTTON_SIZES = {
  sm: 'px-2.5 py-1 text-xs',
  md: 'px-3.5 py-2 text-sm',
} as const;

const BUTTON_VARIANTS = {
  primary: 'bg-brand text-white shadow-xs hover:bg-brand-hover',
  secondary: 'border border-border-strong bg-surface text-ink shadow-xs hover:bg-surface-muted',
  ghost: 'text-ink-muted hover:bg-surface-sunken hover:text-ink',
  danger: 'bg-red-600 text-white shadow-xs hover:bg-red-700',
  dangerOutline: 'border border-red-200 bg-surface text-red-700 hover:bg-red-50',
} as const;

export type ButtonVariant = keyof typeof BUTTON_VARIANTS;

export function buttonClass(
  variant: ButtonVariant = 'primary',
  size: keyof typeof BUTTON_SIZES = 'md',
): string {
  return `${BUTTON_BASE} ${BUTTON_SIZES[size]} ${BUTTON_VARIANTS[variant]}`;
}

/** Khung thẻ: nền trắng, viền mảnh, bóng rất nhẹ — nổi khỏi nền xám mà không nặng nề. */
export const CARD = 'rounded-xl border border-border bg-surface shadow-xs';

export const INPUT =
  'w-full rounded-lg border border-border-strong bg-surface px-3 py-2 text-sm text-ink shadow-xs outline-none transition-shadow placeholder:text-ink-subtle focus:border-brand focus:ring-3 focus:ring-brand-ring disabled:bg-surface-sunken';

/** Liên kết chữ trong đoạn văn. */
export const TEXT_LINK = 'font-medium text-brand hover:text-brand-hover hover:underline';

/** Tiêu đề nhỏ viết hoa của một nhóm nội dung trong thẻ. */
export const SECTION_LABEL = 'text-xs font-semibold tracking-wider text-ink-muted uppercase';

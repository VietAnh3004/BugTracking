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
  'inline-flex cursor-pointer items-center justify-center gap-1.5 rounded-md font-medium whitespace-nowrap transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-50';

// Chiều cao cố định (28 / 32px) thay cho padding dọc: nút đứng cạnh ô nhập, select, badge trên
// cùng một hàng phải thẳng mép — kiểu thanh công cụ IDE.
const BUTTON_SIZES = {
  sm: 'h-7 px-2.5 text-xs',
  md: 'h-8 px-3 text-sm',
} as const;

const BUTTON_VARIANTS = {
  primary: 'bg-brand-solid text-white shadow-xs hover:bg-brand-solid-hover',
  // Hover SÁNG lên một lớp (sunken), không tối đi: ở nền tối "nổi lên" mới đọc ra là đang trỏ vào.
  secondary: 'border border-border-strong bg-surface text-ink hover:bg-surface-sunken',
  ghost: 'text-ink-muted hover:bg-surface-sunken hover:text-ink',
  danger: 'bg-danger-solid text-white shadow-xs hover:bg-danger-solid-hover',
  dangerOutline: 'border border-danger/40 bg-surface text-danger hover:bg-danger/10',
} as const;

export type ButtonVariant = keyof typeof BUTTON_VARIANTS;

export function buttonClass(
  variant: ButtonVariant = 'primary',
  size: keyof typeof BUTTON_SIZES = 'md',
): string {
  return `${BUTTON_BASE} ${BUTTON_SIZES[size]} ${BUTTON_VARIANTS[variant]}`;
}

/** Khung thẻ: nền trắng, viền mảnh, bóng rất nhẹ — nổi khỏi nền xám mà không nặng nề. */
/** Thẻ/panel: phân tách bằng viền 1px, không đổ bóng (xem `src/index.css`). */
export const CARD = 'rounded-lg border border-border bg-surface';

export const INPUT =
  'w-full rounded-md border border-border-strong bg-surface-sunken px-2.5 py-1.5 text-sm text-ink outline-none transition-colors duration-150 placeholder:text-ink-subtle focus:border-brand focus:ring-2 focus:ring-brand-ring disabled:opacity-60';

/** Liên kết chữ trong đoạn văn. */
export const TEXT_LINK = 'font-medium text-brand hover:text-brand-hover hover:underline';

/** Tiêu đề nhỏ viết hoa của một nhóm nội dung trong thẻ. */
export const SECTION_LABEL = 'text-xs font-semibold tracking-wider text-ink-muted uppercase';

/**
 * Định dạng số cho dashboard.
 *
 * ================== VÌ SAO KIỂM `== null` CHỨ KHÔNG `=== undefined` ==================
 * Backend trả `null` thật cho mọi tỉ lệ có mẫu số 0 (Jackson của dự án không bật `NON_NULL`, nên
 * khoá vẫn có mặt trong JSON với giá trị `null`). Nhưng `openapi-typescript` sinh ra kiểu
 * `unsupportedClaimRate?: number` — tức `number | undefined`. Hai thứ đó LỆCH NHAU: TypeScript
 * tin rằng không bao giờ có `null`, còn runtime thì có.
 *
 * Hậu quả nếu viết `value === undefined ? '—' : ...`: nhánh đó KHÔNG chạy với `null`, và
 * `null * 100` trong JavaScript bằng `0`, nên màn hình hiện **`0.0%`** — đúng con số sai nguy hiểm
 * nhất của tuần này. "Chưa ai review" đọc thành "review hết mà 0% khớp", và trình biên dịch không
 * hề cảnh báo vì theo kiểu thì nhánh đó là đủ.
 *
 * `== null` bắt cả `null` lẫn `undefined`, nên nó đúng với cả hợp đồng khai báo lẫn dữ liệu thật.
 * =====================================================================================
 */

/** Chưa đủ dữ liệu để tính. Không bao giờ được thay bằng `0` hay `0%`. */
export const CHUA_CO = '—';

/** Tỉ lệ 0..1 → phần trăm một chữ số thập phân; `null`/`undefined` → dấu gạch. */
export function formatRatio(value: number | null | undefined): string {
  if (value == null) {
    return CHUA_CO;
  }
  return `${(value * 100).toFixed(1)}%`;
}

/** Số thực (mili giây, số vòng lặp) → một chữ số thập phân; `null`/`undefined` → dấu gạch. */
export function formatDecimal(value: number | null | undefined, unit = ''): string {
  if (value == null) {
    return CHUA_CO;
  }
  return `${value.toFixed(1)}${unit}`;
}

/**
 * Số đếm.
 *
 * KHÁC hẳn hai hàm trên: `0` ở đây là một phép đo hợp lệ ("không có nhận định nào không có căn
 * cứ" là tin tốt), không phải "chưa biết". Chỉ tỉ lệ mới có khái niệm chưa tính được.
 */
export function formatCount(value: number): string {
  return value.toLocaleString('vi-VN');
}

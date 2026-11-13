/**
 * Rút gọn `summary` của cảnh báo để hiện trong BẢNG.
 *
 * `summary` do backend dựng cho LLM đọc: một câu TỰ CHỨA, nên nó nhồi đủ công cụ, mức độ, mã luật
 * và vị trí — `[spotbugs/HIGH] WEAK_MESSAGE_DIGEST_MD5: MD5 is weak — vn/.../PasswordHasher.java:40`.
 * Trong bảng, bốn thứ đó đã có cột riêng ngay bên cạnh; in lại chúng làm mỗi hàng dài gấp ba và
 * chìm mất đúng phần người đọc cần: thông điệp của công cụ.
 *
 * Chỉ bóc phần đầu `[tool/SEV] RULE:` và phần đuôi ` — <đường dẫn>` khi chúng ĐÚNG khuôn đó; chuỗi
 * nào không khớp thì trả nguyên văn. Dữ liệu trong DB và thứ Agent đọc không đổi gì.
 */

/** `[spotbugs/HIGH] WEAK_MESSAGE_DIGEST_MD5: ` hoặc `[semgrep/MEDIUM] use-of-md5: ` */
const PREFIX = /^\[[^\]\n]+\]\s*[\w.$-]+:\s+/;

/** ` — vn/hust/.../File.java:40` — đuôi là một đường dẫn (có `/`), không có khoảng trắng. */
const LOCATION_SUFFIX = /\s+—\s+[^\s—]*\/[^\s—]*$/;

export function displaySummary(summary: string): string {
  const trimmed = summary.replace(PREFIX, '').replace(LOCATION_SUFFIX, '').trim();
  return trimmed === '' ? summary : trimmed;
}

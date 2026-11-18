/**
 * Các yêu cầu mật khẩu, hiển thị cho người dùng lúc đặt mật khẩu (đăng ký, đặt lại, đổi).
 *
 * Bản sao của `core-backend/.../dto/NewPassword.java` — CHỐT CHẶN THẬT nằm ở backend, đây chỉ để
 * người dùng thấy từng yêu cầu đạt hay chưa trong lúc gõ thay vì bấm gửi rồi mới biết. Đổi quy tắc
 * thì đổi cả hai chỗ; lệch nhau thì danh sách báo "đạt hết" mà backend vẫn trả 400.
 *
 * Chữ cái tính theo Unicode (`\p{L}`, cờ `u`) để "mậtkhẩu1" hợp lệ, khớp `\p{L}` của Java; chữ số
 * chỉ 0-9, khớp `[0-9]` phía backend.
 */

export interface PasswordRule {
  id: string;
  label: string;
  test: (password: string) => boolean;
}

export const PASSWORD_RULES: readonly PasswordRule[] = [
  { id: 'length', label: 'Tối thiểu 8 ký tự', test: (password) => password.length >= 8 },
  { id: 'letter', label: 'Có ít nhất một chữ cái', test: (password) => /\p{L}/u.test(password) },
  { id: 'digit', label: 'Có ít nhất một chữ số', test: (password) => /[0-9]/.test(password) },
];

/** Yêu cầu đầu tiên chưa đạt, hoặc `null` khi đạt hết — dùng để chặn gửi form ở client. */
export function firstUnmetPasswordRule(password: string): PasswordRule | null {
  return PASSWORD_RULES.find((rule) => !rule.test(password)) ?? null;
}

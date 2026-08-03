/**
 * NƠI DUY NHẤT trong ứng dụng chạm tới `localStorage`.
 *
 * Cùng nguyên tắc với `shared/config/runtime.ts` (nơi duy nhất đọc cấu hình): gom một loại tác
 * dụng phụ về một chỗ thì đổi cách lưu trữ sau này chỉ phải sửa một file, và không ai vô tình lưu
 * thêm thứ khác vào đó.
 *
 * CHỈ refresh token được lưu ở đây. Access token nằm trong bộ nhớ (`apiClient.setAccessToken`) —
 * xem `docs/adr/0006-xac-thuc-jwt-va-refresh-token-xoay-vong.md` cho đánh đổi đầy đủ.
 *
 * Mọi thao tác bọc try/catch vì `localStorage` NÉM LỖI chứ không trả về null trong vài trường hợp
 * thật: Safari ở chế độ riêng tư (quota 0), trình duyệt chặn cookie/storage của bên thứ ba, và
 * iframe sandbox. Một `SecurityError` không bắt ở tầng này sẽ làm trắng cả ứng dụng chỉ vì không
 * đọc được một token có thể không tồn tại.
 */

const REFRESH_TOKEN_KEY = 'datn.refreshToken';

export function readRefreshToken(): string | null {
  try {
    return window.localStorage.getItem(REFRESH_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function writeRefreshToken(token: string): void {
  try {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, token);
  } catch {
    // Không lưu được thì phiên chỉ sống tới khi tải lại trang — vẫn dùng được ứng dụng, nên
    // không nâng thành lỗi chặn người dùng.
  }
}

export function clearRefreshToken(): void {
  try {
    window.localStorage.removeItem(REFRESH_TOKEN_KEY);
  } catch {
    // Xoá thất bại: token vẫn còn nhưng đã bị thu hồi phía server ở luồng logout, nên không có
    // rủi ro thực tế.
  }
}

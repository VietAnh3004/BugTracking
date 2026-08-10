/**
 * Tải file từ backend về máy người dùng.
 *
 * Đây là ngoại lệ **thứ ba** được phép không dùng `apiClient` (sau `uploadClient` và `sseClient`),
 * và cùng một lý do: `apiClient` parse JSON, còn ở đây thân response là CSV hoặc là một file cần
 * giữ nguyên byte. Cũng như hai module kia, nó **dùng lại helper export từ `apiClient`** thay vì
 * chép logic — base URL, cách gắn token và cách bóc lỗi phải giống hệt nhau ở cả bốn đường.
 *
 * Không dùng thẻ `<a download>` trỏ thẳng vào URL: trình duyệt sẽ gửi request đó **không kèm
 * header `Authorization`**, nên backend trả 401 và người dùng nhận một file HTML lỗi mang tên
 * `.csv`. Phải `fetch` rồi tự dựng blob.
 */

import {
  ApiError,
  authorizationHeader,
  parseProblem,
  refreshAccessToken,
  resolveUrl,
} from '@/shared/lib/apiClient';

/**
 * Tải một file và mở hộp thoại lưu của trình duyệt.
 *
 * Làm mới access token **trước**, cùng lý do với `uploadClient` và `sseClient`: không thể "thử lại
 * khi gặp 401" giữa chừng một lần tải, và một file tải dở là một file hỏng.
 */
export async function downloadFile(path: string, fallbackFileName: string): Promise<void> {
  await refreshAccessToken();

  const response = await fetch(resolveUrl(path), {
    method: 'GET',
    headers: { ...authorizationHeader() },
  });

  if (!response.ok) {
    throw new ApiError(response.status, parseProblem(response.status, await response.text()));
  }

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  try {
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = fileNameFrom(response.headers.get('Content-Disposition')) ?? fallbackFileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    // Thu hồi ngay: mỗi object URL giữ cả blob trong bộ nhớ cho tới khi tab đóng, và người dùng
    // tải báo cáo nhiều lần trong một phiên là chuyện bình thường.
    URL.revokeObjectURL(objectUrl);
  }
}

/** Lấy tên file backend đề xuất; `null` khi header vắng hoặc không đúng khuôn. */
function fileNameFrom(contentDisposition: string | null): string | null {
  if (contentDisposition === null) {
    return null;
  }
  const match = /filename="([^"]+)"/.exec(contentDisposition);
  // `match?.[1]` không thu hẹp được kiểu dưới `noUncheckedIndexedAccess`; kiểm tường minh.
  return match === null || match[1] === undefined ? null : match[1];
}

/**
 * NGOẠI LỆ CÓ CHỦ ĐÍCH của quy tắc "mọi request đi qua apiClient" (§1bis.3).
 *
 * Lý do kỹ thuật, không phải tiện tay: `fetch` KHÔNG có sự kiện tiến trình UPLOAD. Cách duy nhất
 * để có nó với `fetch` là truyền `ReadableStream` làm body kèm `duplex: 'half'`, thứ chỉ Chromium
 * hỗ trợ — Firefox và Safari sẽ hỏng. `XMLHttpRequest` có `upload.onprogress` ở mọi trình duyệt.
 * Upload repo là thao tác duy nhất trong ứng dụng đủ lâu để cần thanh tiến trình.
 *
 * Đổi lại ngoại lệ này, module PHẢI dùng lại `resolveUrl` / `authorizationHeader` / `parseProblem`
 * / `ApiError` export từ `apiClient` thay vì chép logic — nếu không, ứng dụng sẽ có hai kiểu hiển
 * thị lỗi và hai cách gắn token khác nhau.
 */

import {
  ApiError,
  authorizationHeader,
  parseProblem,
  refreshAccessToken,
  resolveUrl,
} from '@/shared/lib/apiClient';

export interface UploadOptions {
  onProgress?: (percent: number) => void;
  signal?: AbortSignal;
}

export async function uploadFile<T>(
  path: string,
  file: File,
  options: UploadOptions = {},
): Promise<T> {
  // Làm mới token TRƯỚC khi bắt đầu, không thử-lại-khi-401 giữa chừng: với file lớn, thử lại nghĩa
  // là tải lại từ đầu — người dùng chờ gấp đôi mà vẫn có thể hết hạn lần nữa.
  await refreshAccessToken();

  return new Promise<T>((resolve, reject) => {
    const request = new XMLHttpRequest();
    const formData = new FormData();
    // Tên field phải khớp @RequestParam("file") của SnapshotController.
    formData.append('file', file);

    request.open('POST', resolveUrl(path));

    // TUYỆT ĐỐI không tự đặt Content-Type cho FormData: trình duyệt phải tự sinh
    // `multipart/form-data; boundary=...`. Đặt tay sẽ thiếu boundary và backend không tách được
    // phần nào ra phần nào — lỗi hiện ra dưới dạng 400 khó hiểu ở phía server.
    for (const [name, value] of Object.entries(authorizationHeader())) {
      request.setRequestHeader(name, value);
    }

    if (options.onProgress !== undefined) {
      const onProgress = options.onProgress;
      request.upload.addEventListener('progress', (event) => {
        // `lengthComputable` sai khi trình duyệt không biết tổng kích thước; chia cho 0 sẽ ra NaN
        // và thanh tiến trình biến mất.
        if (event.lengthComputable) {
          onProgress(Math.round((event.loaded / event.total) * 100));
        }
      });
    }

    request.addEventListener('load', () => {
      if (request.status >= 200 && request.status < 300) {
        resolve(
          request.responseText === '' ? (undefined as T) : (JSON.parse(request.responseText) as T),
        );
        return;
      }
      reject(new ApiError(request.status, parseProblem(request.status, request.responseText)));
    });

    // Lỗi mạng và lỗi CORS đều tới đây mà KHÔNG có mã HTTP — nói rõ ra để đỡ mất thời gian đoán,
    // đúng cách `BackendStatus` đã làm.
    request.addEventListener('error', () => {
      reject(new ApiError(0, { title: 'Không gọi được backend (lỗi mạng hoặc CORS)' }));
    });

    request.addEventListener('abort', () => {
      reject(new ApiError(0, { title: 'Đã huỷ tải lên' }));
    });

    options.signal?.addEventListener('abort', () => {
      request.abort();
    });

    request.send(formData);
  });
}

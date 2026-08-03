/**
 * Nơi duy nhất định nghĩa cấu hình TanStack Query của ứng dụng.
 *
 * Là HÀM chứ không phải một instance dùng chung, vì test cần một client MỚI cho mỗi test: cache
 * của QueryClient sống theo instance, dùng chung một instance giữa các test sẽ khiến test sau đọc
 * trúng dữ liệu test trước đã cache và pass/fail tuỳ thứ tự chạy — loại lỗi khó tái hiện nhất.
 * `app/providers.tsx` gọi hàm này đúng một lần ở tầm module cho ứng dụng thật.
 */

import { QueryClient } from '@tanstack/react-query';

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // Không refetch khi chuyển tab: người dùng thường mở song song mã nguồn và tài liệu,
        // refetch liên tục vừa tốn request vừa làm giao diện nhấp nháy.
        refetchOnWindowFocus: false,
      },
    },
  });
}

/**
 * Client cho TEST: tắt retry.
 *
 * Mặc định TanStack Query thử lại 3 lần với backoff luỹ tiến — một test kiểm nhánh lỗi sẽ phải
 * chờ vài giây rồi mới thấy `isError`, và thường là hết giờ (timeout) trước khi kịp. Tắt retry
 * biến "lỗi" thành trạng thái quan sát được ngay lập tức.
 */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
}

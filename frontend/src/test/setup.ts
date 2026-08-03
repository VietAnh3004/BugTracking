/*
 * Setup dùng chung cho mọi test, nạp qua `test.setupFiles` trong vite.config.ts.
 *
 * ============================ QUY TẮC QUAN TRỌNG NHẤT CỦA FILE NÀY ============================
 * KHÔNG được import bất kỳ module nào của ứng dụng ở đây (apiClient, runtime, component...).
 *
 * `shared/config/runtime.ts` gọi `loadAppConfig()` ở TOP LEVEL và ném lỗi nếu thiếu
 * `window.__APP_CONFIG__.apiBaseUrl`. Trong ESM, mọi `import` được nâng lên và thực thi TRƯỚC
 * thân module — nên chỉ cần file này import (dù gián tiếp qua nhiều lớp) tới `runtime.ts`, lỗi sẽ
 * nổ ngay lúc nạp setup, trước khi dòng gán `window.__APP_CONFIG__` bên dưới kịp chạy. Triệu
 * chứng khi vấp phải: TOÀN BỘ test đỏ cùng lúc với "Thiếu cấu hình runtime", kể cả test không
 * liên quan gì tới API.
 *
 * Vitest chạy trọn setupFiles trước khi import file test, nên gán ở đây là đủ sớm cho mọi test.
 * ==============================================================================================
 */

import '@testing-library/jest-dom/vitest';

import { afterEach } from 'vitest';

import { cleanup } from '@testing-library/react';

// Giá trị giả, cố tình khác localhost:8080 thật để test nào vô tình gọi mạng thật sẽ lộ ra ngay
// trong log thay vì âm thầm trúng backend đang chạy trên máy lập trình viên.
window.__APP_CONFIG__ = { apiBaseUrl: 'http://api.test.local' };

// Vitest không tự dọn DOM giữa các test (khác Jest + RTL cấu hình mặc định). Thiếu bước này,
// `screen.getByText` thấy phần tử còn sót từ test trước và báo "found multiple elements".
afterEach(() => {
  cleanup();
});

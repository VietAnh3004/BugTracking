import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
// `defineConfig` lấy từ 'vitest/config' chứ không phải 'vite': chỉ bản này mới biết khoá `test`,
// dùng bản của 'vite' sẽ đỏ kiểu ở `tsc -b`.
import { defineConfig } from 'vitest/config';

export default defineConfig({
  // Tailwind v4 qua plugin Vite chính chủ, KHÔNG qua PostCSS: plugin tự quét source và biên dịch
  // trong pipeline của Vite, nên không cần postcss.config.js, autoprefixer, hay tailwind.config.js.
  plugins: [tailwindcss(), react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // Cố tình KHÔNG cấu hình proxy tới backend.
    //
    // Proxy sẽ khiến trình duyệt thấy mọi request là same-origin, nên suốt quá trình phát triển
    // sẽ không gặp lỗi CORS nào — rồi vỡ đúng lúc deploy tách domain. Gọi thẳng sang
    // http://localhost:8080 để CORS được kiểm tra thật ngay từ ngày đầu.
  },
  test: {
    // happy-dom chứ không phải jsdom — KHÔNG phải sở thích, là bắt buộc trên máy phát triển này.
    // Đo thật (cache đã ấm, lặp nhiều lần): `require('jsdom')` mất ~11 GIÂY mỗi lần, còn
    // `require('happy-dom')` mất ~1-3 giây. jsdom gồm hàng nghìn file nhỏ nên bị Windows Defender
    // quét lại mỗi lần nạp. Hệ quả: mọi worker của Vitest vượt ngưỡng khởi động 60s và toàn bộ
    // test đỏ với "[vitest-pool]: Failed to start forks worker" — thông điệp KHÔNG hề nhắc tới
    // jsdom, rất dễ đi sửa nhầm chỗ (đã mất thời gian thử đổi pool forks/threads trước khi khoanh
    // được vùng bằng cách chạy vitest với config tối giản).
    environment: 'happy-dom',
    setupFiles: ['./src/test/setup.ts'],
    // Không xử lý CSS trong test: component test kiểm hành vi và nội dung, không kiểm màu sắc.
    // Bật lên chỉ tốn thời gian biên dịch Tailwind cho mỗi lần chạy test.
    css: false,
    // Cố tình KHÔNG bật `globals: true`. Phải `import { describe, it, expect } from 'vitest'`
    // tường minh: biến toàn cục ẩn làm TypeScript phải cấu hình thêm `types` và khiến người đọc
    // không biết `expect` đến từ đâu.
    globals: false,
  },
});

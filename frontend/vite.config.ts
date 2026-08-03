import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

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
});

/**
 * Đọc cấu hình runtime do `public/config.js` đặt vào `window`.
 *
 * Đây là điểm duy nhất trong ứng dụng biết backend nằm ở đâu. Không component nào được tự ghép
 * URL — nếu cần gọi API, dùng client trong `shared/lib/apiClient`.
 */

export interface AppConfig {
  apiBaseUrl: string;
}

declare global {
  interface Window {
    __APP_CONFIG__?: Partial<AppConfig>;
  }
}

/**
 * Trả về cấu hình đã kiểm tra hợp lệ.
 *
 * Ném lỗi ngay khi khởi động nếu thiếu `apiBaseUrl`: một màn hình trắng kèm lỗi rõ ràng dễ chẩn
 * đoán hơn nhiều so với việc mọi request lặng lẽ gọi vào `undefined/api/v1/...`.
 */
export function loadAppConfig(): AppConfig {
  const raw = window.__APP_CONFIG__;

  if (!raw?.apiBaseUrl) {
    throw new Error(
      'Thiếu cấu hình runtime: window.__APP_CONFIG__.apiBaseUrl. ' +
        'Kiểm tra public/config.js đã được nạp chưa, hoặc biến API_BASE_URL của container.',
    );
  }

  return {
    // Bỏ dấu / cuối để việc ghép đường dẫn không sinh ra `//api/v1`.
    apiBaseUrl: raw.apiBaseUrl.replace(/\/+$/, ''),
  };
}

export const appConfig: AppConfig = loadAppConfig();

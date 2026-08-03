/**
 * Giữ phiên đăng nhập và khôi phục nó khi tải lại trang.
 *
 * Luồng bootstrap (chạy đúng một lần lúc mount): có refresh token trong localStorage thì đổi lấy
 * access token mới; không có hoặc đổi thất bại thì chuyển sang `anonymous`. Trong suốt thời gian
 * đó trạng thái là `bootstrapping` — route guard PHẢI chờ, xem `ProtectedRoute`.
 *
 * Đây cũng là nơi nối `apiClient` với tầng ứng dụng qua `setAuthHooks`: client hạ tầng không được
 * biết React hay localStorage, nên nó nhận hàm từ đây thay vì tự import (xem Javadoc `apiClient`).
 */

import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import * as authApi from '@/features/auth/api/authApi';
import { AuthContext, type AuthStatus } from '@/features/auth/context/AuthContext';
import { setAccessToken, setAuthHooks } from '@/shared/lib/apiClient';
import { clearRefreshToken, readRefreshToken, writeRefreshToken } from '@/shared/lib/tokenStorage';

import type { CurrentUser } from '@/features/auth/api/authApi';
import type { ReactNode } from 'react';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('bootstrapping');
  const [user, setUser] = useState<CurrentUser | null>(null);
  const queryClient = useQueryClient();

  // React StrictMode gọi effect HAI LẦN trong dev. Nếu không chặn, lần thứ hai sẽ gọi /auth/refresh
  // với refresh token vừa bị xoay ở lần thứ nhất - backend coi đó là TÁI SỬ DỤNG và thu hồi cả họ,
  // đá người dùng ra ngay khi mở ứng dụng. Đây là lỗi chỉ xuất hiện ở môi trường dev nên rất dễ
  // mất thời gian truy tìm.
  const bootstrapStarted = useRef(false);

  const applyTokens = useCallback((tokens: authApi.AuthTokens) => {
    setAccessToken(tokens.accessToken);
    writeRefreshToken(tokens.refreshToken);
    setUser(tokens.user);
    setStatus('authenticated');
  }, []);

  const resetSession = useCallback(() => {
    setAccessToken(null);
    clearRefreshToken();
    setUser(null);
    setStatus('anonymous');
  }, []);

  // Nối apiClient với phiên đăng nhập. Đặt trong effect riêng (không gộp với bootstrap) để việc
  // gỡ hook lúc unmount không kéo theo logic khác.
  useEffect(() => {
    setAuthHooks({
      refresh: async () => {
        const storedToken = readRefreshToken();
        if (storedToken === null) {
          return null;
        }
        const tokens = await authApi.refresh(storedToken);
        // Refresh token XOAY VÒNG: bản cũ vừa bị backend thu hồi, phải ghi đè ngay. Quên bước này
        // thì lần refresh kế tiếp gửi lại token đã dùng và bị coi là tái sử dụng.
        writeRefreshToken(tokens.refreshToken);
        return { accessToken: tokens.accessToken };
      },
      onSessionExpired: () => {
        resetSession();
        // Xoá cache: dữ liệu của người dùng vừa hết phiên không được hiện lại cho người đăng nhập
        // sau trên cùng trình duyệt.
        queryClient.clear();
      },
    });

    return () => setAuthHooks(null);
  }, [queryClient, resetSession]);

  useEffect(() => {
    if (bootstrapStarted.current) {
      return;
    }
    bootstrapStarted.current = true;

    const storedToken = readRefreshToken();
    if (storedToken === null) {
      setStatus('anonymous');
      return;
    }

    authApi
      .refresh(storedToken)
      .then(applyTokens)
      .catch(() => {
        // Refresh token hết hạn hoặc đã bị thu hồi — dọn sạch rồi coi như chưa đăng nhập. Không
        // hiện lỗi: với người dùng thì đây chỉ là "phiên cũ đã hết", không phải sự cố.
        resetSession();
      });
  }, [applyTokens, resetSession]);

  const signOut = useCallback(async () => {
    const storedToken = readRefreshToken();
    if (storedToken !== null) {
      // Bỏ qua lỗi có chủ đích: dù server từ chối (token đã hết hạn, mất mạng), phía client vẫn
      // phải đăng xuất. Đăng xuất thất bại vì lỗi mạng là hành vi tệ hơn nhiều so với việc để lại
      // một token đằng nào cũng sắp hết hạn.
      await authApi.logout(storedToken).catch(() => undefined);
    }
    resetSession();
    queryClient.clear();
  }, [resetSession, queryClient]);

  const value = useMemo(
    () => ({ status, user, signIn: applyTokens, signOut }),
    [status, user, applyTokens, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Giữ phiên đăng nhập và khôi phục nó khi tải lại trang.
 *
 * Luồng bootstrap (chạy đúng một lần lúc mount): có refresh token trong localStorage thì đổi lấy
 * access token mới; không có hoặc đổi thất bại thì chuyển sang `anonymous`. Trong suốt thời gian
 * đó trạng thái là `bootstrapping` — route guard PHẢI chờ, xem `ProtectedRoute`.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import * as authApi from '@/features/auth/api/authApi';
import { AuthContext, type AuthStatus } from '@/features/auth/context/AuthContext';
import { setAccessToken } from '@/shared/lib/apiClient';
import { clearRefreshToken, readRefreshToken, writeRefreshToken } from '@/shared/lib/tokenStorage';

import type { CurrentUser } from '@/features/auth/api/authApi';
import type { ReactNode } from 'react';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('bootstrapping');
  const [user, setUser] = useState<CurrentUser | null>(null);

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
  }, [resetSession]);

  const value = useMemo(
    () => ({ status, user, signIn: applyTokens, signOut }),
    [status, user, applyTokens, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

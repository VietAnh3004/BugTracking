import { useMutation } from '@tanstack/react-query';

import * as authApi from '@/features/auth/api/authApi';
import { useAuth } from '@/features/auth/hooks/useAuth';

/**
 * Đăng nhập rồi nạp phiên vào context. Không tự điều hướng ở đây — việc đó là của trang gọi nó
 * (`LoginPage`), để hook dùng lại được ở nơi khác mà không kéo theo một quyết định điều hướng.
 */
export function useLogin() {
  const { signIn } = useAuth();

  return useMutation({
    mutationFn: (body: authApi.LoginRequest) => authApi.login(body),
    onSuccess: signIn,
  });
}

import { useMutation } from '@tanstack/react-query';

import * as authApi from '@/features/auth/api/authApi';
import { useAuth } from '@/features/auth/hooks/useAuth';

/** Đăng ký xong là đăng nhập luôn — backend trả về cùng bộ token như `/auth/login`. */
export function useRegister() {
  const { signIn } = useAuth();

  return useMutation({
    mutationFn: (body: authApi.RegisterRequest) => authApi.register(body),
    onSuccess: signIn,
  });
}

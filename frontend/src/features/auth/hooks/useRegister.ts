import { useMutation } from '@tanstack/react-query';

import * as authApi from '@/features/auth/api/authApi';

/**
 * Đăng ký KHÔNG đăng nhập luôn: backend chỉ tạo tài khoản chưa xác thực và gửi link vào email.
 * Phiên đầu tiên đến từ trang `/verify-email` khi người dùng mở link đó.
 */
export function useRegister() {
  return useMutation({
    mutationFn: (body: authApi.RegisterRequest) => authApi.register(body),
  });
}

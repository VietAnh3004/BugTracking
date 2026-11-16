/**
 * Hook cho các luồng tài khoản ngoài đăng nhập/đăng ký. Hook nào nhận về một PHIÊN mới (xác thực
 * email, Google, đổi mật khẩu) đều nạp nó vào context ngay — trang gọi chỉ còn lo điều hướng.
 */

import { useMutation, useQuery } from '@tanstack/react-query';

import * as authApi from '@/features/auth/api/authApi';
import { useAuth } from '@/features/auth/hooks/useAuth';

/** Cấu hình gần như không đổi trong một phiên chạy của backend — không cần tải lại. */
export function useAuthConfig() {
  return useQuery({
    queryKey: ['auth', 'config'],
    queryFn: authApi.fetchAuthConfig,
    staleTime: Infinity,
    retry: false,
  });
}

export function useVerifyEmail() {
  const { signIn } = useAuth();
  return useMutation({ mutationFn: authApi.verifyEmail, onSuccess: signIn });
}

export function useResendVerification() {
  return useMutation({ mutationFn: authApi.resendVerification });
}

export function useForgotPassword() {
  return useMutation({ mutationFn: authApi.forgotPassword });
}

export function useResetPassword() {
  return useMutation({
    mutationFn: ({ token, newPassword }: { token: string; newPassword: string }) =>
      authApi.resetPassword(token, newPassword),
  });
}

export function useGoogleLogin() {
  const { signIn } = useAuth();
  return useMutation({ mutationFn: authApi.loginWithGoogle, onSuccess: signIn });
}

export function useUpdateProfile() {
  const { updateUser } = useAuth();
  return useMutation({ mutationFn: authApi.updateProfile, onSuccess: updateUser });
}

/**
 * Backend đóng MỌI phiên cũ — kể cả phiên đang dùng — rồi trả một phiên mới. Phải nạp phiên mới
 * ngay, nếu không lần refresh kế tiếp gửi refresh token đã bị thu hồi và người dùng bị đá ra.
 */
export function useChangePassword() {
  const { signIn } = useAuth();
  return useMutation({ mutationFn: authApi.changePassword, onSuccess: signIn });
}

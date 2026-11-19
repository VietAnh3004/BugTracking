/**
 * Đăng nhập quản trị — công khai nhưng KHÔNG có link nào trỏ tới (người quản trị tự gõ địa chỉ).
 *
 * Dùng chính `/api/v1/auth/login` như đăng nhập thường: admin chỉ là một tài khoản có vai trò ADMIN,
 * khác ở chỗ "email" của nó là tên đăng nhập do `ADMIN_USERNAME` đặt (không cần là email) — nên ô nhập
 * ở đây là văn bản thường, không phải `type="email"`.
 *
 * Không có "Quên mật khẩu", "Đăng ký" hay "Đăng nhập Google": mật khẩu admin đổi bằng `.env`.
 */

import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';

import { AuthForm } from '@/features/auth/components/AuthForm';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { useLogin } from '@/features/auth/hooks/useLogin';
import { ApiError } from '@/shared/lib/apiClient';
import { TextField } from '@/shared/ui/TextField';

import type { FormEvent } from 'react';

export function AdminLoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const login = useLogin();
  const { status, user } = useAuth();

  // Đã đăng nhập bằng tài khoản admin thì không cần gõ lại.
  if (status === 'authenticated' && user?.role === 'ADMIN') {
    return <Navigate to="/admin" replace />;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    login.mutate(
      { email: username.trim(), password },
      { onSuccess: () => void navigate('/admin', { replace: true }) },
    );
  }

  return (
    <AuthForm
      title="Quản trị"
      subtitle="Chỉ dành cho quản trị viên."
      submitLabel="Đăng nhập"
      pending={login.isPending}
      errorMessage={login.isError ? describeError(login.error) : null}
      onSubmit={handleSubmit}
    >
      <TextField
        id="username"
        label="Tên đăng nhập"
        type="text"
        value={username}
        onChange={setUsername}
        autoComplete="username"
      />
      <TextField
        id="password"
        label="Mật khẩu"
        type="password"
        value={password}
        onChange={setPassword}
        autoComplete="current-password"
      />
    </AuthForm>
  );
}

/** Cùng nguyên tắc với trang đăng nhập thường: không nói sai ở tên đăng nhập hay ở mật khẩu. */
function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.status === 401
      ? 'Tên đăng nhập hoặc mật khẩu không đúng.'
      : (error.problem.detail ?? error.message);
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

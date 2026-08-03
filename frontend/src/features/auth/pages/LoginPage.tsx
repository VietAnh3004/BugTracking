import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { AuthForm } from '@/features/auth/components/AuthForm';
import { useLogin } from '@/features/auth/hooks/useLogin';
import { ApiError } from '@/shared/lib/apiClient';
import { TextField } from '@/shared/ui/TextField';

import type { FormEvent } from 'react';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const login = useLogin();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    login.mutate(
      { email, password },
      {
        // Điều hướng về `/` chứ không phải `/projects`: `PublicOnlyRoute` sẽ đọc `state.from` mà
        // `ProtectedRoute` đặt trước đó và đưa người dùng về đúng trang họ định mở.
        onSuccess: () => void navigate('/', { replace: true }),
      },
    );
  }

  return (
    <AuthForm
      title="Đăng nhập"
      submitLabel="Đăng nhập"
      pending={login.isPending}
      errorMessage={login.isError ? describeLoginError(login.error) : null}
      onSubmit={handleSubmit}
      footer={
        <>
          Chưa có tài khoản?{' '}
          <Link to="/register" className="text-brand">
            Đăng ký
          </Link>
        </>
      }
    >
      <TextField
        id="email"
        label="Email"
        type="email"
        value={email}
        onChange={setEmail}
        autoComplete="email"
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

/**
 * Backend cố tình trả CÙNG một thông điệp cho "email không tồn tại" và "sai mật khẩu" (chống dò
 * tài khoản) — frontend không được suy diễn thêm để "thân thiện hơn", làm vậy là phá chính biện
 * pháp đó.
 */
function describeLoginError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.status === 401
      ? 'Email hoặc mật khẩu không đúng.'
      : (error.problem.detail ?? error.message);
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

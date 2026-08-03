import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { AuthField, AuthForm } from '@/features/auth/components/AuthForm';
import { useRegister } from '@/features/auth/hooks/useRegister';
import { ApiError } from '@/shared/lib/apiClient';

import type { FormEvent } from 'react';

export function RegisterPage() {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const register = useRegister();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    register.mutate(
      { email, displayName, password },
      { onSuccess: () => void navigate('/', { replace: true }) },
    );
  }

  return (
    <AuthForm
      title="Đăng ký"
      submitLabel="Tạo tài khoản"
      pending={register.isPending}
      errorMessage={register.isError ? describeRegisterError(register.error) : null}
      onSubmit={handleSubmit}
      footer={
        <>
          Đã có tài khoản?{' '}
          <Link to="/login" className="text-brand">
            Đăng nhập
          </Link>
        </>
      }
    >
      <AuthField
        id="email"
        label="Email"
        type="email"
        value={email}
        onChange={setEmail}
        autoComplete="email"
      />
      <AuthField
        id="displayName"
        label="Tên hiển thị"
        type="text"
        value={displayName}
        onChange={setDisplayName}
        autoComplete="name"
      />
      <AuthField
        id="password"
        label="Mật khẩu"
        type="password"
        value={password}
        onChange={setPassword}
        autoComplete="new-password"
      />
    </AuthForm>
  );
}

function describeRegisterError(error: unknown): string {
  if (error instanceof ApiError) {
    // 409 là email đã dùng — ở luồng ĐĂNG KÝ thì nói thẳng được, vì người dùng vốn đã biết email
    // họ vừa nhập; giấu đi chỉ khiến họ không hiểu tại sao không tạo được tài khoản.
    if (error.status === 409) {
      return 'Email này đã được đăng ký.';
    }
    return error.problem.detail ?? error.message;
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

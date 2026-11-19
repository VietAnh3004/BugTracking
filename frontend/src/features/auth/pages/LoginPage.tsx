import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { AuthForm } from '@/features/auth/components/AuthForm';
import { GoogleLoginSection } from '@/features/auth/components/GoogleLoginSection';
import { useResendVerification } from '@/features/auth/hooks/useAccount';
import { useLogin } from '@/features/auth/hooks/useLogin';
import { ApiError } from '@/shared/lib/apiClient';
import { buttonClass, TEXT_LINK } from '@/shared/ui/styles';
import { TextField } from '@/shared/ui/TextField';

import type { FormEvent } from 'react';

const EMAIL_NOT_VERIFIED = 'https://datn.hust.vn/problems/email-not-verified';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const login = useLogin();
  const resend = useResendVerification();

  const notVerified =
    login.error instanceof ApiError && login.error.problem.type === EMAIL_NOT_VERIFIED;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    resend.reset();
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
      subtitle="Tiếp tục phân tích cảnh báo dựa trên bằng chứng."
      submitLabel="Đăng nhập"
      pending={login.isPending}
      errorMessage={login.isError && !notVerified ? describeLoginError(login.error) : null}
      onSubmit={handleSubmit}
      after={
        <>
          {notVerified ? (
            <div
              className="border-warning/30 bg-warning/10 text-warning mt-4 flex flex-col gap-2 rounded-lg border px-3 py-3 text-sm"
              role="alert"
            >
              <p className="m-0">
                Email này chưa được xác thực. Mở link trong email chúng tôi đã gửi tới{' '}
                <strong>{email}</strong>, hoặc gửi lại email mới.
              </p>
              {resend.isSuccess ? (
                <p className="m-0 font-medium">Đã gửi lại. Kiểm tra cả hộp thư Spam.</p>
              ) : (
                <button
                  type="button"
                  onClick={() => resend.mutate(email)}
                  disabled={resend.isPending}
                  className={`${buttonClass('secondary', 'sm')} self-start`}
                >
                  {resend.isPending ? 'Đang gửi…' : 'Gửi lại email xác thực'}
                </button>
              )}
            </div>
          ) : null}
          <GoogleLoginSection />
        </>
      }
      footer={
        <>
          Chưa có tài khoản?{' '}
          <Link to="/register" className={TEXT_LINK}>
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
      <div className="flex flex-col gap-1">
        <TextField
          id="password"
          label="Mật khẩu"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
        />
        <Link to="/forgot-password" className={`${TEXT_LINK} self-end text-xs`}>
          Quên mật khẩu?
        </Link>
      </div>
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

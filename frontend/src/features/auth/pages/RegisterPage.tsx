import { useState } from 'react';
import { Link } from 'react-router-dom';

import { AuthForm } from '@/features/auth/components/AuthForm';
import { CheckInboxNotice } from '@/features/auth/components/CheckInboxNotice';
import { GoogleLoginSection } from '@/features/auth/components/GoogleLoginSection';
import { useResendVerification } from '@/features/auth/hooks/useAccount';
import { useRegister } from '@/features/auth/hooks/useRegister';
import { ApiError } from '@/shared/lib/apiClient';
import { TEXT_LINK } from '@/shared/ui/styles';
import { TextField } from '@/shared/ui/TextField';

import type { FormEvent } from 'react';

export function RegisterPage() {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const register = useRegister();
  const resend = useResendVerification();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    register.mutate({ email, displayName, password });
  }

  if (register.isSuccess) {
    const sentTo = register.data.email;
    return (
      <CheckInboxNotice
        title="Kiểm tra hộp thư"
        onResend={() => resend.mutate(sentTo)}
        resendPending={resend.isPending}
        resent={resend.isSuccess}
      >
        <p className="m-0">
          Chúng tôi đã gửi link xác thực tới <strong className="text-ink">{sentTo}</strong>. Mở link
          đó để kích hoạt tài khoản và đăng nhập.
        </p>
        <p className="m-0">Không thấy thư? Kiểm tra mục Spam, hoặc gửi lại sau một phút.</p>
      </CheckInboxNotice>
    );
  }

  return (
    <AuthForm
      title="Đăng ký"
      subtitle="Tạo tài khoản để tải mã nguồn lên và phân tích."
      submitLabel="Tạo tài khoản"
      pending={register.isPending}
      errorMessage={register.isError ? describeRegisterError(register.error) : null}
      onSubmit={handleSubmit}
      after={<GoogleLoginSection text="signup_with" />}
      footer={
        <>
          Đã có tài khoản?{' '}
          <Link to="/login" className={TEXT_LINK}>
            Đăng nhập
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
        id="displayName"
        label="Tên hiển thị"
        type="text"
        value={displayName}
        onChange={setDisplayName}
        autoComplete="name"
      />
      <TextField
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
      return 'Email này đã được đăng ký. Quên mật khẩu thì dùng "Quên mật khẩu?" ở trang đăng nhập.';
    }
    return error.problem.detail ?? error.message;
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

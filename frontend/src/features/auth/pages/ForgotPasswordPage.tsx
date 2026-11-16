import { useState } from 'react';
import { Link } from 'react-router-dom';

import { AuthForm } from '@/features/auth/components/AuthForm';
import { CheckInboxNotice } from '@/features/auth/components/CheckInboxNotice';
import { useForgotPassword } from '@/features/auth/hooks/useAccount';
import { ApiError } from '@/shared/lib/apiClient';
import { TEXT_LINK } from '@/shared/ui/styles';
import { TextField } from '@/shared/ui/TextField';

import type { FormEvent } from 'react';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const forgot = useForgotPassword();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    forgot.mutate(email);
  }

  if (forgot.isSuccess) {
    return (
      <CheckInboxNotice title="Kiểm tra hộp thư">
        {/* "NẾU": backend không tiết lộ email có tài khoản hay không, frontend cũng không được. */}
        <p className="m-0">
          Nếu <strong className="text-ink">{email}</strong> có tài khoản, một link đặt lại mật khẩu
          vừa được gửi tới đó. Link có hiệu lực 30 phút và chỉ dùng được một lần.
        </p>
        <p className="m-0">Tài khoản tạo bằng Google cũng dùng link này để đặt mật khẩu lần đầu.</p>
      </CheckInboxNotice>
    );
  }

  return (
    <AuthForm
      title="Quên mật khẩu"
      subtitle="Nhập email tài khoản, chúng tôi sẽ gửi link đặt mật khẩu mới."
      submitLabel="Gửi link đặt lại"
      pending={forgot.isPending}
      errorMessage={forgot.isError ? describeError(forgot.error) : null}
      onSubmit={handleSubmit}
      footer={
        <>
          Nhớ ra rồi?{' '}
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
    </AuthForm>
  );
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem.detail ?? error.message;
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

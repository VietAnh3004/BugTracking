import { useState } from 'react';
import { Link } from 'react-router-dom';

import { AuthForm } from '@/features/auth/components/AuthForm';
import { CheckInboxNotice } from '@/features/auth/components/CheckInboxNotice';
import { GoogleLoginSection } from '@/features/auth/components/GoogleLoginSection';
import { PasswordRequirements } from '@/features/auth/components/PasswordRequirements';
import { useResendVerification } from '@/features/auth/hooks/useAccount';
import { useRegister } from '@/features/auth/hooks/useRegister';
import { firstUnmetPasswordRule } from '@/features/auth/lib/passwordRules';
import { ApiError } from '@/shared/lib/apiClient';
import { TEXT_LINK } from '@/shared/ui/styles';
import { TextField } from '@/shared/ui/TextField';

import type { FormEvent } from 'react';

export function RegisterPage() {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  // Đã bấm gửi khi mật khẩu còn thiếu yêu cầu: danh sách tô đỏ dòng chưa đạt. Cố ý không thêm
  // dòng cảnh báo riêng — danh sách ngay dưới ô nhập đã nói đủ thiếu gì.
  const [flagUnmet, setFlagUnmet] = useState(false);
  const register = useRegister();
  const resend = useResendVerification();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // Kiểm trước ở client để khỏi tốn một lượt gọi; backend vẫn kiểm lại (@NewPassword).
    if (firstUnmetPasswordRule(password) !== null) {
      setFlagUnmet(true);
      document.getElementById('password')?.focus();
      return;
    }
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
      <div className="flex flex-col gap-2">
        <TextField
          id="password"
          label="Mật khẩu"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
          describedBy="password-requirements"
        />
        <PasswordRequirements
          id="password-requirements"
          password={password}
          flagUnmet={flagUnmet}
        />
      </div>
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
    // 400 validate: backend nói rõ field nào sai (vd "Mật khẩu phải có ít nhất một chữ số") —
    // hiện câu đó thay vì tiêu đề chung "Dữ liệu đầu vào không hợp lệ".
    const firstFieldError = Object.values(error.problem.fieldErrors ?? {})[0];
    if (firstFieldError !== undefined) {
      return firstFieldError;
    }
    return error.problem.detail ?? error.message;
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

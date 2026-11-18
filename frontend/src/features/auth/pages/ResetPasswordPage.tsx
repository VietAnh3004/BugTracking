/**
 * Đích của link đặt lại mật khẩu trong email.
 *
 * Đứng NGOÀI `PublicOnlyRoute` như `VerifyEmailPage`. Nếu người mở link đang đăng nhập thì sau khi
 * đặt xong vẫn phải đăng xuất ở đây: backend đã thu hồi mọi refresh token của tài khoản, giữ phiên
 * cũ trong trình duyệt chỉ làm lần refresh kế tiếp hỏng một cách khó hiểu.
 */

import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { AuthForm } from '@/features/auth/components/AuthForm';
import { PasswordRequirements } from '@/features/auth/components/PasswordRequirements';
import { useResetPassword } from '@/features/auth/hooks/useAccount';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { firstUnmetPasswordRule } from '@/features/auth/lib/passwordRules';
import { tokenFromHash } from '@/features/auth/lib/tokenFromHash';
import { ApiError } from '@/shared/lib/apiClient';
import { IconCheck } from '@/shared/ui/icons';
import { buttonClass, CARD, TEXT_LINK } from '@/shared/ui/styles';
import { TextField } from '@/shared/ui/TextField';

import type { FormEvent } from 'react';

export function ResetPasswordPage() {
  const location = useLocation();
  const token = tokenFromHash(location.hash);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);
  const reset = useResetPassword();
  const { status, signOut } = useAuth();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (token === null) {
      return;
    }
    // Kiểm trước ở client cho nhanh; backend vẫn kiểm lại (@NewPassword) — đây chỉ là tiện lợi.
    const unmet = firstUnmetPasswordRule(password);
    if (unmet !== null) {
      setLocalError(`Mật khẩu chưa đạt yêu cầu: ${unmet.label.toLowerCase()}.`);
      return;
    }
    if (password !== confirm) {
      setLocalError('Hai lần nhập mật khẩu không khớp.');
      return;
    }
    setLocalError(null);
    reset.mutate(
      { token, newPassword: password },
      {
        onSuccess: () => {
          if (status === 'authenticated') {
            void signOut();
          }
        },
      },
    );
  }

  if (token === null) {
    return (
      <section className="mx-auto w-full max-w-md pt-4 sm:pt-10">
        <div className={`${CARD} flex flex-col items-center gap-4 p-6 text-center sm:p-8`}>
          <h2 className="text-ink m-0 text-xl font-semibold tracking-tight">Link không hợp lệ</h2>
          <p className="text-ink-muted m-0 text-sm" role="alert">
            Link đặt lại mật khẩu thiếu mã. Hãy mở lại đúng link trong email, hoặc yêu cầu link mới.
          </p>
          <Link to="/forgot-password" className={buttonClass('primary')}>
            Yêu cầu link mới
          </Link>
        </div>
      </section>
    );
  }

  if (reset.isSuccess) {
    return (
      <section className="mx-auto w-full max-w-md pt-4 sm:pt-10">
        <div className={`${CARD} flex flex-col items-center gap-4 p-6 text-center sm:p-8`}>
          <span className="grid size-12 place-items-center rounded-xl bg-emerald-50 text-emerald-600">
            <IconCheck className="size-6" />
          </span>
          <h2 className="text-ink m-0 text-xl font-semibold tracking-tight">Đã đặt mật khẩu mới</h2>
          <p className="text-ink-muted m-0 text-sm">
            Mọi phiên đăng nhập cũ đã bị đăng xuất. Đăng nhập lại bằng mật khẩu mới.
          </p>
          <Link to="/login" className={buttonClass('primary')}>
            Đăng nhập
          </Link>
        </div>
      </section>
    );
  }

  return (
    <AuthForm
      title="Đặt mật khẩu mới"
      subtitle="Mật khẩu mới thay cho mật khẩu cũ, và đăng xuất mọi phiên đang mở."
      submitLabel="Đặt mật khẩu"
      pending={reset.isPending}
      errorMessage={localError ?? (reset.isError ? describeError(reset.error) : null)}
      onSubmit={handleSubmit}
      footer={
        <Link to="/forgot-password" className={TEXT_LINK}>
          Link hết hạn? Yêu cầu link mới
        </Link>
      }
    >
      <div className="flex flex-col gap-2">
        <TextField
          id="newPassword"
          label="Mật khẩu mới"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
          describedBy="password-requirements"
        />
        <PasswordRequirements id="password-requirements" password={password} />
      </div>
      <TextField
        id="confirmPassword"
        label="Nhập lại mật khẩu mới"
        type="password"
        value={confirm}
        onChange={setConfirm}
        autoComplete="new-password"
      />
    </AuthForm>
  );
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const firstFieldError = Object.values(error.problem.fieldErrors ?? {})[0];
    if (firstFieldError !== undefined) {
      return firstFieldError;
    }
    return error.problem.detail ?? error.message;
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

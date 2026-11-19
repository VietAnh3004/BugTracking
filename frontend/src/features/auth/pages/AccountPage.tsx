/**
 * Trang tài khoản: hồ sơ (tên hiển thị), ảnh đại diện, mật khẩu.
 *
 * Ảnh đại diện KHÔNG tải lên hệ thống này (quyết định của người dùng): ảnh lấy từ Google nếu đã
 * liên kết, không thì từ Gravatar theo email. "Đổi ảnh" vì vậy là một liên kết ra nơi giữ ảnh.
 */

import { useState } from 'react';

import { PasswordRequirements } from '@/features/auth/components/PasswordRequirements';
import { useChangePassword, useUpdateProfile } from '@/features/auth/hooks/useAccount';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { firstUnmetPasswordRule } from '@/features/auth/lib/passwordRules';
import { ApiError } from '@/shared/lib/apiClient';
import { Avatar } from '@/shared/ui/Avatar';
import { PageHeader } from '@/shared/ui/PageHeader';
import { buttonClass, CARD, SECTION_LABEL, TEXT_LINK } from '@/shared/ui/styles';
import { TextField } from '@/shared/ui/TextField';

import type { CurrentUser } from '@/features/auth/api/authApi';
import type { FormEvent, ReactNode } from 'react';

export function AccountPage() {
  const { user } = useAuth();
  // ProtectedRoute chỉ cho vào khi đã có người dùng; nhánh này chỉ để TypeScript thu hẹp kiểu.
  if (user === null) {
    return null;
  }
  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Tài khoản"
        description="Thông tin hiển thị với thành viên khác và cách bạn đăng nhập."
        back={{ to: '/projects', label: 'Dự án' }}
      />
      <ProfileSection user={user} />
      <AvatarSection user={user} />
      <PasswordSection user={user} />
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className={`${CARD} flex flex-col gap-4 p-4`}>
      <h2 className={`${SECTION_LABEL} m-0`}>{title}</h2>
      {children}
    </section>
  );
}

function ProfileSection({ user }: { user: CurrentUser }) {
  const [displayName, setDisplayName] = useState(user.displayName);
  const update = useUpdateProfile();
  const unchanged = displayName.trim() === user.displayName;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    update.mutate(displayName.trim());
  }

  return (
    <Section title="Hồ sơ">
      <div className="flex items-center gap-4">
        <Avatar displayName={user.displayName} avatarUrl={user.avatarUrl} size="lg" />
        <div className="min-w-0">
          <p className="text-ink m-0 truncate text-base font-semibold">{user.displayName}</p>
          <p className="text-ink-muted m-0 truncate text-sm">{user.email}</p>
          <div className="mt-1.5 flex flex-wrap gap-1.5">
            <Badge tone={user.emailVerified ? 'good' : 'warn'}>
              {user.emailVerified ? 'Email đã xác thực' : 'Email chưa xác thực'}
            </Badge>
            {user.googleLinked ? <Badge tone="neutral">Đã liên kết Google</Badge> : null}
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="flex-1">
          <TextField
            id="displayName"
            label="Tên hiển thị"
            value={displayName}
            onChange={(value) => {
              update.reset();
              setDisplayName(value);
            }}
            autoComplete="name"
          />
        </div>
        <button
          type="submit"
          disabled={update.isPending || unchanged || displayName.trim() === ''}
          className={buttonClass('primary')}
        >
          {update.isPending ? 'Đang lưu…' : 'Lưu tên'}
        </button>
      </form>
      <Feedback
        success={update.isSuccess ? 'Đã lưu tên hiển thị.' : null}
        error={update.isError ? describeError(update.error) : null}
      />
    </Section>
  );
}

function AvatarSection({ user }: { user: CurrentUser }) {
  return (
    <Section title="Ảnh đại diện">
      {user.googleLinked ? (
        <p className="text-ink-muted m-0 text-sm">
          Ảnh lấy từ tài khoản Google của bạn và cập nhật mỗi lần bạn đăng nhập bằng Google. Muốn
          đổi ảnh, đổi ảnh hồ sơ trong{' '}
          <a
            href="https://myaccount.google.com/personal-info"
            target="_blank"
            rel="noopener noreferrer"
            className={TEXT_LINK}
          >
            tài khoản Google
          </a>
          .
        </p>
      ) : (
        <p className="text-ink-muted m-0 text-sm">
          Ảnh lấy từ Gravatar theo email <strong className="text-ink">{user.email}</strong>. Muốn
          đổi ảnh, đăng ký hoặc đổi ảnh tại{' '}
          <a
            href="https://gravatar.com/profile/avatars"
            target="_blank"
            rel="noopener noreferrer"
            className={TEXT_LINK}
          >
            gravatar.com
          </a>{' '}
          bằng đúng email này (ảnh mới có thể mất vài phút mới hiện). Chưa có ảnh thì hệ thống hiện
          chữ cái đầu của tên.
        </p>
      )}
    </Section>
  );
}

function PasswordSection({ user }: { user: CurrentUser }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);
  const change = useChangePassword();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const unmet = firstUnmetPasswordRule(next);
    if (unmet !== null) {
      setLocalError(`Mật khẩu mới chưa đạt yêu cầu: ${unmet.label.toLowerCase()}.`);
      return;
    }
    if (next !== confirm) {
      setLocalError('Hai lần nhập mật khẩu mới không khớp.');
      return;
    }
    setLocalError(null);
    change.mutate(
      user.hasPassword ? { currentPassword: current, newPassword: next } : { newPassword: next },
      {
        onSuccess: () => {
          setCurrent('');
          setNext('');
          setConfirm('');
        },
      },
    );
  }

  return (
    <Section title={user.hasPassword ? 'Đổi mật khẩu' : 'Đặt mật khẩu'}>
      {user.hasPassword ? null : (
        <p className="text-ink-muted m-0 text-sm">
          Tài khoản này đang đăng nhập bằng Google và chưa có mật khẩu. Đặt mật khẩu để đăng nhập
          được cả bằng email.
        </p>
      )}
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        {user.hasPassword ? (
          <TextField
            id="currentPassword"
            label="Mật khẩu hiện tại"
            type="password"
            value={current}
            onChange={setCurrent}
            autoComplete="current-password"
          />
        ) : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField
            id="newPassword"
            label="Mật khẩu mới"
            type="password"
            value={next}
            onChange={setNext}
            autoComplete="new-password"
            describedBy="password-requirements"
          />
          <TextField
            id="confirmPassword"
            label="Nhập lại mật khẩu mới"
            type="password"
            value={confirm}
            onChange={setConfirm}
            autoComplete="new-password"
          />
        </div>
        <PasswordRequirements id="password-requirements" password={next} />
        <p className="text-ink-muted m-0 text-xs">
          Đổi xong, mọi thiết bị khác đang đăng nhập tài khoản này sẽ bị đăng xuất.
        </p>
        <button
          type="submit"
          disabled={change.isPending}
          className={`${buttonClass('primary')} self-start`}
        >
          {change.isPending ? 'Đang lưu…' : user.hasPassword ? 'Đổi mật khẩu' : 'Đặt mật khẩu'}
        </button>
      </form>
      <Feedback
        success={change.isSuccess ? 'Đã cập nhật mật khẩu.' : null}
        error={localError ?? (change.isError ? describeError(change.error) : null)}
      />
    </Section>
  );
}

function Badge({ tone, children }: { tone: 'good' | 'warn' | 'neutral'; children: ReactNode }) {
  const tones = {
    good: 'bg-success/10 text-success border-success/30',
    warn: 'bg-warning/10 text-warning border-warning/30',
    neutral: 'bg-surface-muted text-ink-muted border-border',
  } as const;
  return (
    <span
      className={`inline-flex h-5 items-center rounded-sm border px-1.5 text-xs font-medium ${tones[tone]}`}
    >
      {children}
    </span>
  );
}

function Feedback({ success, error }: { success: string | null; error: string | null }) {
  if (error !== null) {
    return (
      <p
        className="border-danger/30 bg-danger/10 text-danger m-0 rounded-lg border px-3 py-2 text-sm"
        role="alert"
      >
        {error}
      </p>
    );
  }
  if (success !== null) {
    return (
      <p className="text-success m-0 text-sm font-medium" role="status">
        {success}
      </p>
    );
  }
  return null;
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const fieldErrors = error.problem.fieldErrors;
    if (fieldErrors !== undefined) {
      const first = Object.values(fieldErrors)[0];
      if (first !== undefined) {
        return first;
      }
    }
    return error.problem.detail ?? error.message;
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

/**
 * Khung form dùng chung cho đăng nhập và đăng ký — hai trang khác nhau đúng ở tập trường và nhãn
 * nút, nên tách phần chung ra đây thay vì chép hai lần cách hiển thị lỗi và trạng thái đang gửi.
 *
 * Thuần trình bày: không gọi API, không biết `useLogin`/`useRegister` tồn tại.
 */

import { IconLogo } from '@/shared/ui/icons';
import { buttonClass, CARD } from '@/shared/ui/styles';

import type { FormEvent, ReactNode } from 'react';

interface AuthFormProps {
  title: string;
  /** Một câu dưới tiêu đề nói màn hình này để làm gì. */
  subtitle?: string;
  submitLabel: string;
  pending: boolean;
  errorMessage: string | null;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  children: ReactNode;
  footer: ReactNode;
}

export function AuthForm({
  title,
  subtitle,
  submitLabel,
  pending,
  errorMessage,
  onSubmit,
  children,
  footer,
}: AuthFormProps) {
  return (
    <section className="mx-auto w-full max-w-md pt-4 sm:pt-10">
      <div className={`${CARD} p-6 sm:p-8`}>
        <div className="mb-6 flex flex-col items-center text-center">
          <span className="bg-brand-subtle text-brand mb-4 grid size-12 place-items-center rounded-xl">
            <IconLogo className="size-6" />
          </span>
          <h2 className="text-ink m-0 text-xl font-semibold tracking-tight">{title}</h2>
          {subtitle === undefined ? null : (
            <p className="text-ink-muted mt-1 mb-0 text-sm">{subtitle}</p>
          )}
        </div>

        <form onSubmit={onSubmit} className="flex flex-col gap-4">
          {children}

          {/* role="alert" để trình đọc màn hình thông báo ngay, và để test tìm lỗi theo vai trò. */}
          {errorMessage !== null ? (
            <p
              className="m-0 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
              role="alert"
            >
              {errorMessage}
            </p>
          ) : null}

          <button
            type="submit"
            disabled={pending}
            className={`${buttonClass('primary')} mt-1 w-full`}
          >
            {pending ? 'Đang xử lý…' : submitLabel}
          </button>
        </form>
      </div>

      <p className="text-ink-muted mt-5 text-center text-sm">{footer}</p>
    </section>
  );
}

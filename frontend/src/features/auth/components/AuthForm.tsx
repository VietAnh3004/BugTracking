/**
 * Khung form dùng chung cho đăng nhập và đăng ký — hai trang khác nhau đúng ở tập trường và nhãn
 * nút, nên tách phần chung ra đây thay vì chép hai lần cách hiển thị lỗi và trạng thái đang gửi.
 *
 * Thuần trình bày: không gọi API, không biết `useLogin`/`useRegister` tồn tại.
 */

import type { FormEvent, ReactNode } from 'react';

interface AuthFormProps {
  title: string;
  submitLabel: string;
  pending: boolean;
  errorMessage: string | null;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  children: ReactNode;
  footer: ReactNode;
}

export function AuthForm({
  title,
  submitLabel,
  pending,
  errorMessage,
  onSubmit,
  children,
  footer,
}: AuthFormProps) {
  return (
    <section className="mx-auto max-w-sm">
      <h2 className="mt-0 mb-4 text-lg font-semibold">{title}</h2>

      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        {children}

        {/* role="alert" để trình đọc màn hình thông báo ngay, và để test tìm lỗi theo vai trò. */}
        {errorMessage !== null ? (
          <p className="text-sm text-red-700" role="alert">
            {errorMessage}
          </p>
        ) : null}

        <button
          type="submit"
          disabled={pending}
          className="bg-brand hover:bg-brand-hover cursor-pointer rounded px-4 py-2 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
        >
          {pending ? 'Đang xử lý…' : submitLabel}
        </button>
      </form>

      <p className="text-ink-muted mt-4 text-sm">{footer}</p>
    </section>
  );
}

/** Ô nhập có nhãn gắn đúng `htmlFor`/`id` — thiếu cặp này thì `getByLabelText` trong test không tìm ra. */
export function AuthField({
  id,
  label,
  type,
  value,
  onChange,
  autoComplete,
  required = true,
}: {
  id: string;
  label: string;
  type: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: string;
  required?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        required={required}
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
        className="border-border focus:border-brand rounded border px-3 py-2 outline-none"
      />
    </div>
  );
}

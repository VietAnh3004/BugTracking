/**
 * Phần đầu thống nhất của mọi màn hình: đường quay lại, tiêu đề, mô tả ngắn và nhóm hành động.
 *
 * Trước đây mỗi trang tự dựng phần này theo một kiểu (link "←" chỗ thì trên, chỗ thì cạnh nút;
 * tiêu đề chỗ h1 chỗ h2), nên chuyển trang là bố cục nhảy. Gom về một chỗ thì mắt người dùng luôn
 * tìm thấy nút "quay lại" và hành động chính ở cùng vị trí.
 */

import { Link } from 'react-router-dom';

import { IconArrowLeft } from '@/shared/ui/icons';

import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  back?: { to: string; label: string };
  /** Nút hoặc liên kết hành động, căn phải trên màn hình rộng. */
  actions?: ReactNode;
  /** Dòng phụ ngay dưới tiêu đề, ví dụ mã snapshot hay chip trạng thái. */
  meta?: ReactNode;
}

export function PageHeader({ title, description, back, actions, meta }: PageHeaderProps) {
  return (
    <header className="flex flex-col gap-3">
      {back === undefined ? null : (
        <Link
          to={back.to}
          className="text-ink-muted hover:text-brand inline-flex items-center gap-1 self-start text-sm font-medium"
        >
          <IconArrowLeft className="size-3.5" />
          {back.label}
        </Link>
      )}
      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-3">
        <div className="min-w-0">
          <h1 className="text-ink m-0 text-2xl font-semibold tracking-tight">{title}</h1>
          {description === undefined ? null : (
            <p className="text-ink-muted mt-1 mb-0 max-w-3xl text-sm">{description}</p>
          )}
          {meta === undefined ? null : (
            <div className="mt-2 flex flex-wrap items-center gap-2">{meta}</div>
          )}
        </div>
        {actions === undefined ? null : (
          <div className="flex flex-wrap items-center gap-2">{actions}</div>
        )}
      </div>
    </header>
  );
}

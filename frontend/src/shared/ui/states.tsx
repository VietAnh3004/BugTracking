/**
 * Ba trạng thái mà MỌI màn hình có dữ liệu phải có: đang tải, trống, lỗi.
 *
 * - Đang tải: SKELETON mang hình dạng của thứ sắp hiện (bảng, khối mã, trang chi tiết), không phải
 *   spinner giữa trang. Spinner không nói gì về bố cục, và khi dữ liệu về thì cả trang nhảy;
 *   skeleton giữ chỗ đúng hình nên bố cục đứng yên.
 * - Trống: nói rõ vì sao trống và BƯỚC TIẾP THEO là gì (một nút bấm được), không chỉ "Không có dữ
 *   liệu".
 * - Lỗi: lỗi gì + cách khắc phục + thử lại. `role="alert"` để trình đọc màn hình báo ngay.
 *
 * Thuần trình bày: không gọi API, không biết dữ liệu nào đang tải.
 */

import { IconError } from '@/shared/ui/icons';
import { buttonClass } from '@/shared/ui/styles';

import type { ReactNode } from 'react';

/** Một khối xám giữ chỗ. Kích thước do nơi gọi đặt qua `className` (vd `h-4 w-40`). */
export function Skeleton({ className = 'h-4 w-full' }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`bg-surface-raised block animate-[skeleton-pulse_1.6s_ease-in-out_infinite] rounded-sm ${className}`}
    />
  );
}

// Độ rộng cố định chứ không ngẫu nhiên: ngẫu nhiên thì mỗi lần render một khác, skeleton "nhảy".
const LINE_WIDTHS = ['w-3/4', 'w-full', 'w-5/6', 'w-2/3', 'w-11/12', 'w-1/2', 'w-4/5', 'w-3/5'];

type LoadingShape = 'lines' | 'table' | 'code' | 'detail';

interface LoadingStateProps {
  /** Đọc cho trình đọc màn hình và hiện nhỏ phía trên — người dùng biết ĐANG tải cái gì. */
  label: string;
  /** Hình dạng của nội dung sắp hiện. */
  shape?: LoadingShape;
  rows?: number;
}

export function LoadingState({ label, shape = 'lines', rows = 6 }: LoadingStateProps) {
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-3 py-2">
      <span className="text-ink-subtle text-xs">{label}</span>
      {shape === 'table' ? <TableSkeleton rows={rows} /> : null}
      {shape === 'code' ? <CodeSkeleton rows={rows} /> : null}
      {shape === 'detail' ? <DetailSkeleton /> : null}
      {shape === 'lines' ? <LinesSkeleton rows={rows} /> : null}
    </div>
  );
}

function LinesSkeleton({ rows }: { rows: number }) {
  return (
    <div className="flex flex-col gap-2.5">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className={`h-3.5 ${LINE_WIDTHS[i % LINE_WIDTHS.length]}`} />
      ))}
    </div>
  );
}

function TableSkeleton({ rows }: { rows: number }) {
  return (
    <div className="border-border overflow-hidden rounded-lg border">
      <div className="border-border bg-surface flex gap-4 border-b px-3 py-2.5">
        {['w-16', 'w-24', 'w-48', 'w-20'].map((width) => (
          <Skeleton key={width} className={`h-3 ${width}`} />
        ))}
      </div>
      {Array.from({ length: rows }, (_, i) => (
        <div
          key={i}
          className="border-border flex items-center gap-4 border-b px-3 py-3 last:border-b-0"
        >
          <Skeleton className="h-4 w-16" />
          <Skeleton className="h-3.5 w-24" />
          <Skeleton className={`h-3.5 ${i % 2 === 0 ? 'w-72' : 'w-56'}`} />
          <Skeleton className="h-3.5 w-20" />
        </div>
      ))}
    </div>
  );
}

function CodeSkeleton({ rows }: { rows: number }) {
  return (
    <div className="border-border bg-code-bg flex flex-col gap-2 rounded-lg border p-3">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex gap-4">
          <Skeleton className="h-3 w-6" />
          <Skeleton className={`h-3 ${LINE_WIDTHS[(i * 3) % LINE_WIDTHS.length]}`} />
        </div>
      ))}
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-2">
        <Skeleton className="h-5 w-16" />
        <Skeleton className="h-5 w-24" />
      </div>
      <Skeleton className="h-6 w-2/3" />
      <Skeleton className="h-3.5 w-1/3" />
      <CodeSkeleton rows={6} />
    </div>
  );
}

interface EmptyStateProps {
  title: string;
  /** Vì sao trống, nói bằng ngôn ngữ của người dùng. */
  description?: ReactNode;
  /** Bước tiếp theo — một nút hoặc link. Bỏ trống chỉ khi thật sự không có gì để làm. */
  action?: ReactNode;
  icon?: ReactNode;
}

export function EmptyState({ title, description, action, icon }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-10 text-center">
      {icon === undefined ? null : <span className="text-ink-subtle mb-1">{icon}</span>}
      <p className="text-ink m-0 text-sm font-medium">{title}</p>
      {description === undefined ? null : (
        <p className="text-ink-muted m-0 max-w-md text-sm">{description}</p>
      )}
      {action === undefined ? null : <div className="mt-2">{action}</div>}
    </div>
  );
}

interface ErrorStateProps {
  title: string;
  /** Lỗi cụ thể (thường là `detail` của ProblemDetail từ backend). */
  message?: string;
  /** Cách khắc phục — người dùng làm gì tiếp. */
  hint?: ReactNode;
  onRetry?: () => void;
}

export function ErrorState({ title, message, hint, onRetry }: ErrorStateProps) {
  return (
    <div
      role="alert"
      className="border-danger/30 bg-danger/5 flex gap-3 rounded-lg border px-4 py-3 text-sm"
    >
      <IconError className="text-danger mt-0.5 size-4" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="text-ink m-0 font-medium">{title}</p>
        {message === undefined ? null : <p className="text-ink-muted m-0 break-words">{message}</p>}
        {hint === undefined ? null : <div className="text-ink-muted">{hint}</div>}
        {onRetry === undefined ? null : (
          <div className="mt-1.5">
            <button type="button" onClick={onRetry} className={buttonClass('secondary', 'sm')}>
              Thử lại
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

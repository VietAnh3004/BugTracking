/**
 * Trạng thái chờ chiếm trọn vùng nội dung. Thuần trình bày, không biết gì về xác thực — dùng cho
 * mọi lúc ứng dụng chưa quyết định được hiển thị gì.
 *
 * `role="status"` + `aria-live="polite"` để trình đọc màn hình thông báo, và để test tìm được
 * trạng thái chờ bằng vai trò thay vì bám vào class CSS.
 */

export function FullPageSpinner({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center py-12" role="status" aria-live="polite">
      <span
        className="border-border border-t-brand mr-3 inline-block size-5 animate-spin rounded-full border-2"
        aria-hidden="true"
      />
      <span className="text-ink-muted">{label}</span>
    </div>
  );
}

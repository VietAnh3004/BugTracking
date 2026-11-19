/**
 * Đích của link xác thực trong email. Gửi token lên backend NGAY khi mở trang (không bắt bấm thêm
 * nút): mở link chính là hành động xác nhận. Thành công thì backend trả luôn một phiên đăng nhập.
 *
 * Đứng NGOÀI `PublicOnlyRoute`: người đang đăng nhập bằng tài khoản A vẫn phải mở được link xác
 * thực của tài khoản B (vd vừa đăng ký thêm một tài khoản) — guard đó sẽ đá họ đi trước khi token
 * kịp được dùng.
 */

import { useEffect, useRef } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { useVerifyEmail } from '@/features/auth/hooks/useAccount';
import { tokenFromHash } from '@/features/auth/lib/tokenFromHash';
import { ApiError } from '@/shared/lib/apiClient';
import { LoadingState } from '@/shared/ui/states';
import { buttonClass, CARD } from '@/shared/ui/styles';

export function VerifyEmailPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const verify = useVerifyEmail();
  const { mutate } = verify;
  const token = tokenFromHash(location.hash);
  // Token dùng MỘT lần: StrictMode chạy effect hai lần trong dev, lần thứ hai sẽ nhận 400 "đã dùng"
  // và ghi đè màn hình thành công bằng màn hình lỗi.
  const started = useRef(false);

  useEffect(() => {
    if (token === null || started.current) {
      return;
    }
    started.current = true;
    mutate(token);
  }, [token, mutate]);

  // Điều hướng theo TRẠNG THÁI, không theo `onSuccess` truyền vào `mutate`: callback đó gắn với
  // observer lúc gọi, observer bị gỡ (StrictMode, điều hướng giữa chừng) là nó không bao giờ chạy.
  // `replace` xoá token khỏi lịch sử trình duyệt — link đã dùng, không cần nằm lại đó.
  const succeeded = verify.isSuccess;
  useEffect(() => {
    if (succeeded) {
      void navigate('/projects', { replace: true });
    }
  }, [succeeded, navigate]);

  if (token === null) {
    return <VerifyFailed message="Link xác thực thiếu mã. Hãy mở lại đúng link trong email." />;
  }
  if (verify.isError) {
    return <VerifyFailed message={describeError(verify.error)} />;
  }
  return <LoadingState label="Đang xác thực email…" shape="lines" rows={3} />;
}

function VerifyFailed({ message }: { message: string }) {
  return (
    <section className="mx-auto w-full max-w-md pt-4 sm:pt-10">
      <div className={`${CARD} flex flex-col items-center gap-4 p-6 text-center sm:p-8`}>
        <h2 className="text-ink m-0 text-xl font-semibold tracking-tight">Không xác thực được</h2>
        <p className="text-ink-muted m-0 text-sm" role="alert">
          {message}
        </p>
        <p className="text-ink-muted m-0 text-sm">
          Đăng nhập bằng email và mật khẩu, màn hình đăng nhập có nút gửi lại email xác thực.
        </p>
        <Link to="/login" className={buttonClass('primary')}>
          Tới trang đăng nhập
        </Link>
      </div>
    </section>
  );
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem.detail ?? error.message;
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

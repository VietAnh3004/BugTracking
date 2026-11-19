/**
 * Màn hình "kiểm tra hộp thư" sau khi gửi một email tài khoản (đăng ký, quên mật khẩu).
 *
 * Câu chữ cho "quên mật khẩu" cố ý viết "NẾU email có tài khoản": backend không cho biết email đó
 * có tồn tại hay không (chống dò tài khoản), nên frontend cũng không được khẳng định "đã gửi".
 */

import { Link } from 'react-router-dom';

import { IconCheck } from '@/shared/ui/icons';
import { buttonClass, CARD, TEXT_LINK } from '@/shared/ui/styles';

import type { ReactNode } from 'react';

interface CheckInboxNoticeProps {
  title: string;
  children: ReactNode;
  /** Gửi lại thư; vắng mặt thì không hiện nút. */
  onResend?: () => void;
  resendPending?: boolean;
  resent?: boolean;
}

export function CheckInboxNotice({
  title,
  children,
  onResend,
  resendPending = false,
  resent = false,
}: CheckInboxNoticeProps) {
  return (
    <section className="mx-auto w-full max-w-md pt-4 sm:pt-10">
      <div className={`${CARD} flex flex-col items-center gap-3 p-4 text-center sm:p-6`}>
        {/* Icon mang NGHĨA (đã gửi xong), không trang trí — nên giữ icon, bỏ ô nền to. */}
        <IconCheck className="text-success size-6" />
        <h2 className="text-ink m-0 text-xl font-semibold tracking-tight">{title}</h2>
        <div className="text-ink-muted flex flex-col gap-2 text-sm">{children}</div>
        {onResend === undefined ? null : resent ? (
          <p className="text-success m-0 text-sm font-medium" role="status">
            Đã gửi lại. Nếu vẫn không thấy, kiểm tra hộp thư Spam.
          </p>
        ) : (
          <button
            type="button"
            onClick={onResend}
            disabled={resendPending}
            className={buttonClass('secondary')}
          >
            {resendPending ? 'Đang gửi…' : 'Gửi lại email'}
          </button>
        )}
      </div>
      <p className="text-ink-muted mt-5 text-center text-sm">
        <Link to="/login" className={TEXT_LINK}>
          Quay lại đăng nhập
        </Link>
      </p>
    </section>
  );
}

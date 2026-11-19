/**
 * Khung xác nhận xoá một tài khoản — liệt kê CỤ THỂ những gì sẽ mất, và đòi gõ lại email. Cùng khuôn với
 * `DeleteProjectButton`, nhưng hậu quả lớn hơn nên phải nói rõ số lượng thay vì một câu chung chung.
 *
 * Việc xoá không hoàn tác được, và nó kéo theo dữ liệu của người KHÁC (thành viên trong dự án của tài
 * khoản này mất hội thoại của họ ở đó) — đó là điều admin phải thấy trước khi bấm.
 */

import { useState } from 'react';

import { useDeleteAdminUser } from '@/features/admin/hooks/useAdminUsers';
import { ApiError } from '@/shared/lib/apiClient';
import { buttonClass } from '@/shared/ui/styles';
import { TextField } from '@/shared/ui/TextField';

import type { AdminUser } from '@/features/admin/api/adminApi';
import type { FormEvent } from 'react';

interface DeleteUserPanelProps {
  user: AdminUser;
  onClose: () => void;
}

export function DeleteUserPanel({ user, onClose }: DeleteUserPanelProps) {
  const [typed, setTyped] = useState('');
  const deleteUser = useDeleteAdminUser();

  // So khớp chính xác (kể cả hoa/thường): gõ lại đúng thứ đang hiển thị là bằng chứng admin đã nhìn.
  const matches = typed === user.email;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (matches) {
      deleteUser.mutate(user.id, { onSuccess: onClose });
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="border-danger/30 bg-danger/5 flex flex-col gap-3 rounded-lg border p-4"
    >
      <p className="m-0 text-sm">
        Xoá vĩnh viễn tài khoản <strong>{user.email}</strong>. <strong>Không thể hoàn tác.</strong>{' '}
        Sẽ mất:
      </p>
      <ul className="text-ink-muted m-0 flex list-disc flex-col gap-1 pl-5 text-sm">
        <li>
          <strong className="text-ink">{user.ownedProjectCount}</strong> dự án họ sở hữu, kèm toàn
          bộ snapshot, cảnh báo, file đã tải lên và hội thoại của <em>mọi người</em> trong các dự án
          đó (thành viên được chia sẻ cũng mất phần của họ ở đó).
        </li>
        <li>
          <strong className="text-ink">{user.conversationCount}</strong> hội thoại với Agent của họ,
          và các đánh giá họ đã ghi trên dự án của người khác.
        </li>
        <li>Tư cách thành viên và mọi phiên đăng nhập.</li>
      </ul>
      <p className="text-ink-muted m-0 text-xs">
        Được giữ lại: nhãn ground truth do họ gán với vai trò người gán thứ hai ở dự án của người
        khác (thuộc dữ liệu thực nghiệm của chủ dự án).
      </p>

      <TextField
        id={`delete-user-${user.id}`}
        label="Gõ lại email của tài khoản để xác nhận"
        value={typed}
        onChange={setTyped}
        autoComplete="off"
      />

      {deleteUser.isError ? (
        <p className="text-danger m-0 text-sm" role="alert">
          {describeError(deleteUser.error)}
        </p>
      ) : null}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={!matches || deleteUser.isPending}
          className={buttonClass('danger')}
        >
          {deleteUser.isPending ? 'Đang xoá…' : 'Xoá vĩnh viễn'}
        </button>
        <button
          type="button"
          onClick={onClose}
          disabled={deleteUser.isPending}
          className={buttonClass('secondary')}
        >
          Huỷ
        </button>
      </div>
    </form>
  );
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem.detail ?? error.message;
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}

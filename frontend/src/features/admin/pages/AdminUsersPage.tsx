/**
 * Quản lý tài khoản (khu vực quản trị). Hiện danh sách mọi tài khoản kèm số dự án và hội thoại họ
 * có — đúng những con số mà việc xoá sẽ kéo theo — và cho xoá từng tài khoản.
 *
 * Quy mô đồ án nên danh sách tải một lần, không phân trang; ô tìm kiếm lọc ở backend (theo email hoặc
 * tên hiển thị) để sau này tăng người dùng không phải đổi cách làm.
 */

import { Fragment, useEffect, useState } from 'react';

import { useAdminUsers } from '@/features/admin/hooks/useAdminUsers';
import { DeleteUserPanel } from '@/features/admin/components/DeleteUserPanel';
import { ApiError } from '@/shared/lib/apiClient';
import { IconSearch } from '@/shared/ui/icons';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/states';
import { buttonClass, CARD, INPUT } from '@/shared/ui/styles';

import type { AdminUser } from '@/features/admin/api/adminApi';

const SEARCH_DEBOUNCE_MS = 300;

export function AdminUsersPage() {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  // Chờ người dùng ngừng gõ rồi mới gọi backend: mỗi phím một request là phí và làm bảng nhảy.
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [search]);

  const users = useAdminUsers(query);

  return (
    <section className="flex flex-col gap-4">
      <PageHeader
        title="Quản lý tài khoản"
        description="Xoá một tài khoản là xoá cả dự án, snapshot, bằng chứng và hội thoại của họ. Không hoàn tác được."
      />

      <div className={`${CARD} flex items-center gap-2 px-3 py-2.5`}>
        <IconSearch className="text-ink-subtle size-4" />
        <label htmlFor="admin-user-search" className="sr-only">
          Tìm tài khoản theo email hoặc tên
        </label>
        <input
          id="admin-user-search"
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Tìm theo email hoặc tên hiển thị…"
          autoComplete="off"
          className={`${INPUT} max-w-md`}
        />
        {users.isSuccess ? (
          <span className="text-ink-subtle ml-auto text-xs">{users.data.length} tài khoản</span>
        ) : null}
      </div>

      {users.isPending ? (
        <LoadingState label="Đang tải danh sách tài khoản…" shape="table" />
      ) : null}
      {users.isError ? (
        <ErrorState
          title="Không tải được danh sách tài khoản."
          message={users.error instanceof ApiError ? users.error.message : undefined}
          hint="Kiểm tra trạng thái backend ở chân trang rồi tải lại trang."
          onRetry={() => void users.refetch()}
        />
      ) : null}
      {users.isSuccess && users.data.length === 0 ? (
        <div className={CARD}>
          <EmptyState
            icon={<IconSearch className="size-5" />}
            title="Không có tài khoản nào khớp."
            description="Tìm theo một phần email hoặc tên hiển thị, không phân biệt hoa thường."
          />
        </div>
      ) : null}
      {users.isSuccess && users.data.length > 0 ? (
        <div className={`${CARD} overflow-x-auto`}>
          <table className="w-full min-w-[56rem] border-collapse text-sm">
            <thead>
              <tr className="border-border text-ink-subtle text-2xs border-b text-left font-medium tracking-wider uppercase">
                <th className="py-2 pr-3 pl-3 font-medium">Tài khoản</th>
                <th className="py-2 pr-3 font-medium">Vai trò</th>
                <th className="py-2 pr-3 font-medium">Đăng nhập bằng</th>
                <th className="py-2 pr-3 font-medium">Ngày tạo</th>
                <th className="py-2 pr-3 text-right font-medium">Dự án</th>
                <th className="py-2 pr-3 text-right font-medium">Hội thoại</th>
                <th className="py-2 pr-3 font-medium">
                  <span className="sr-only">Thao tác</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-border divide-y">
              {users.data.map((user) => (
                <Fragment key={user.id}>
                  <UserRow
                    user={user}
                    onDelete={() => setConfirmingId(confirmingId === user.id ? null : user.id)}
                  />
                  {confirmingId === user.id ? (
                    <tr>
                      <td colSpan={7} className="p-3">
                        <DeleteUserPanel user={user} onClose={() => setConfirmingId(null)} />
                      </td>
                    </tr>
                  ) : null}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

function UserRow({ user, onDelete }: { user: AdminUser; onDelete: () => void }) {
  const isAdmin = user.role === 'ADMIN';
  return (
    <tr className="hover:bg-surface-sunken align-top">
      <td className="py-2 pr-3 pl-3">
        <span className="text-ink block font-medium">{user.displayName}</span>
        <span className="text-ink-muted block font-mono text-xs break-all">{user.email}</span>
      </td>
      <td className="py-2 pr-3">
        <StatusBadge
          tone={isAdmin ? 'warning' : 'neutral'}
          label={isAdmin ? 'Quản trị' : 'Người dùng'}
        />
      </td>
      <td className="text-ink-muted py-2 pr-3 text-xs">
        {[user.hasPassword ? 'Mật khẩu' : null, user.googleLinked ? 'Google' : null]
          .filter((method) => method !== null)
          .join(' · ') || '—'}
        {user.emailVerified ? null : (
          <span className="text-warning block">Chưa xác thực email</span>
        )}
      </td>
      <td className="text-ink-muted py-2 pr-3 text-xs tabular-nums">
        {new Date(user.createdAt).toLocaleDateString('vi-VN')}
      </td>
      <td className="text-ink py-2 pr-3 text-right tabular-nums">{user.ownedProjectCount}</td>
      <td className="text-ink py-2 pr-3 text-right tabular-nums">{user.conversationCount}</td>
      <td className="py-2 pr-3 text-right">
        {/* Tài khoản admin không xoá được (backend cũng từ chối): ẩn nút thay vì để bấm rồi báo lỗi. */}
        {isAdmin ? null : (
          <button
            type="button"
            onClick={onDelete}
            aria-label={`Xoá tài khoản ${user.email}`}
            className={buttonClass('dangerOutline', 'sm')}
          >
            Xoá
          </button>
        )}
      </td>
    </tr>
  );
}

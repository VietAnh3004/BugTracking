/**
 * Quản lý thành viên dự án — chỉ hiện cho chủ dự án.
 *
 * Thành viên được: xem mã, cảnh báo, bằng chứng; hỏi Agent; đánh giá; gán nhãn ground truth với
 * vai trò người gán thứ hai (mù). Câu mô tả quyền nằm ngay trong panel để chủ dự án biết mình
 * đang cho người kia làm gì TRƯỚC khi bấm mời.
 */

import { useState } from 'react';

import {
  useAddMember,
  useProjectMembers,
  useRemoveMember,
} from '@/features/projects/hooks/useProjectMembers';
import { ApiError } from '@/shared/lib/apiClient';
import { InfoTip } from '@/shared/ui/InfoTip';
import { buttonClass, CARD, INPUT } from '@/shared/ui/styles';

import type { FormEvent } from 'react';

export function MembersPanel({ projectId }: { projectId: string }) {
  const members = useProjectMembers(projectId, true);
  const add = useAddMember(projectId);
  const remove = useRemoveMember(projectId);
  const [email, setEmail] = useState('');

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    add.mutate(email.trim(), { onSuccess: () => setEmail('') });
  }

  return (
    <section className={`${CARD} p-4`}>
      <div className="mb-4 flex items-center gap-1.5">
        <h2 className="text-ink m-0 text-base font-semibold">Thành viên</h2>
        <InfoTip label="Giải thích: Thành viên">
          Thành viên xem được mã và bằng chứng, hỏi Agent, đánh giá, và gán nhãn với vai trò người
          gán thứ hai — không thấy nhãn của bạn. Họ không xoá, không tải mã, không chạy phân tích
          được.
        </InfoTip>
      </div>

      <form onSubmit={handleSubmit} className="flex gap-2">
        <label htmlFor="member-email" className="sr-only">
          Email thành viên
        </label>
        <input
          id="member-email"
          type="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="email đã đăng ký của người đó"
          className={INPUT}
        />
        <button type="submit" disabled={add.isPending} className={buttonClass('primary')}>
          Mời
        </button>
      </form>
      {add.isError ? (
        <p role="alert" className="text-danger mt-2 mb-0 text-sm">
          {add.error instanceof ApiError
            ? (add.error.problem.detail ?? add.error.message)
            : 'Không gọi được backend.'}
        </p>
      ) : null}

      {members.data === undefined || members.data.length === 0 ? (
        <p className="text-ink-muted mt-4 mb-0 text-sm">Chưa chia sẻ cho ai.</p>
      ) : (
        <ul className="divide-border border-border m-0 mt-4 list-none divide-y rounded-lg border p-0">
          {members.data.map((member) => (
            <li key={member.userId} className="flex items-center justify-between gap-3 px-3 py-2">
              <span className="min-w-0">
                <span className="text-ink block truncate text-sm font-medium">
                  {member.displayName}
                </span>
                <span className="text-ink-muted block truncate text-xs">{member.email}</span>
              </span>
              <button
                type="button"
                onClick={() => remove.mutate(member.userId)}
                disabled={remove.isPending}
                className={buttonClass('ghost', 'sm')}
                aria-label={`Gỡ ${member.displayName}`}
              >
                Gỡ
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

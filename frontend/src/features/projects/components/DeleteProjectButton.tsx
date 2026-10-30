/**
 * Nút xoá dự án, kèm bước xác nhận bằng cách gõ lại tên dự án.
 *
 * Bắt gõ tên chứ không chỉ hỏi "Bạn chắc chứ?": xoá dự án kéo theo MỌI thứ bên dưới, kể cả hội
 * thoại với Agent và quyết định review — dữ liệu đo của thực nghiệm, không tạo lại được. Một hộp
 * OK/Huỷ thì bấm theo phản xạ được; gõ đúng tên thì không.
 *
 * Trả về nút hoặc khung xác nhận (`basis-full`), để hàng chứa nó dùng `flex-wrap` là khung tự
 * xuống một dòng riêng, chiếm hết bề ngang.
 */

import { useState } from 'react';

import { useDeleteProject } from '@/features/projects/hooks/useDeleteProject';
import { ApiError } from '@/shared/lib/apiClient';
import { TextField } from '@/shared/ui/TextField';

import type { FormEvent } from 'react';

interface DeleteProjectButtonProps {
  projectId: string;
  projectName: string;
}

export function DeleteProjectButton({ projectId, projectName }: DeleteProjectButtonProps) {
  const [confirming, setConfirming] = useState(false);
  const [typedName, setTypedName] = useState('');
  const deleteProject = useDeleteProject();

  const nameMatches = typedName === projectName;

  function close() {
    setConfirming(false);
    setTypedName('');
    deleteProject.reset();
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (nameMatches) {
      deleteProject.mutate(projectId);
    }
  }

  if (!confirming) {
    return (
      <button
        type="button"
        onClick={() => setConfirming(true)}
        className="cursor-pointer rounded border border-red-300 px-3 py-1 text-sm text-red-700 hover:bg-red-50"
      >
        Xoá
      </button>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="flex basis-full flex-col gap-3 rounded border border-red-300 bg-red-50 p-4"
    >
      <p className="m-0 text-sm">
        Xoá vĩnh viễn dự án <strong>{projectName}</strong> cùng toàn bộ snapshot, cảnh báo, hội
        thoại với Agent và kết quả review. <strong>Không thể hoàn tác.</strong>
      </p>

      <TextField
        id={`delete-project-${projectId}`}
        label="Gõ tên dự án để xác nhận"
        value={typedName}
        onChange={setTypedName}
        autoComplete="off"
      />

      {deleteProject.isError ? (
        <p className="m-0 text-sm text-red-700" role="alert">
          {describeError(deleteProject.error)}
        </p>
      ) : null}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={!nameMatches || deleteProject.isPending}
          className="cursor-pointer rounded bg-red-700 px-4 py-2 font-semibold text-white hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {deleteProject.isPending ? 'Đang xoá…' : 'Xoá vĩnh viễn'}
        </button>
        <button
          type="button"
          onClick={close}
          disabled={deleteProject.isPending}
          className="border-border cursor-pointer rounded border bg-white px-4 py-2 disabled:cursor-not-allowed disabled:opacity-60"
        >
          Huỷ
        </button>
      </div>
    </form>
  );
}

function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Không gọi được backend. Kiểm tra kết nối mạng.';
  }
  return error.problem.detail ?? error.message;
}

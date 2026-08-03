/**
 * Form tạo dự án — lối vào ĐẦU TIÊN của toàn bộ luồng nghiệp vụ: không có dự án thì không có chỗ
 * để upload repo, nên cũng không xem được file nào.
 */

import { useState } from 'react';

import { useCreateProject } from '@/features/projects/hooks/useCreateProject';
import { ApiError } from '@/shared/lib/apiClient';
import { TextField } from '@/shared/ui/TextField';

import type { FormEvent } from 'react';

export function CreateProjectForm() {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const createProject = useCreateProject();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createProject.mutate(
      // Backend khai `description` là optional; gửi chuỗi rỗng sẽ lưu một chuỗi rỗng thay vì NULL.
      { name, description: description === '' ? undefined : description },
      {
        onSuccess: () => {
          setName('');
          setDescription('');
        },
      },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="border-border flex flex-col gap-3 rounded border p-4">
      <TextField id="project-name" label="Tên dự án" value={name} onChange={setName} />
      <TextField
        id="project-description"
        label="Mô tả (không bắt buộc)"
        value={description}
        onChange={setDescription}
        required={false}
      />

      {createProject.isError ? (
        <p className="text-sm text-red-700" role="alert">
          {describeError(createProject.error)}
        </p>
      ) : null}

      <button
        type="submit"
        disabled={createProject.isPending}
        className="bg-brand hover:bg-brand-hover cursor-pointer self-start rounded px-4 py-2 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
      >
        {createProject.isPending ? 'Đang tạo…' : 'Tạo dự án'}
      </button>
    </form>
  );
}

function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Không gọi được backend. Kiểm tra kết nối mạng.';
  }
  // Lỗi validate của backend trả kèm `fieldErrors`; hiện chúng thay vì một thông điệp chung chung.
  const fieldErrors = error.problem.fieldErrors;
  if (fieldErrors !== undefined) {
    return Object.values(fieldErrors).join(' ');
  }
  return error.problem.detail ?? error.message;
}

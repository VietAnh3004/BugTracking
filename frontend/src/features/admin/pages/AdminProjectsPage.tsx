/**
 * Quản lý dự án (khu vực quản trị): xem mọi dự án và đổi hạng Free/Premium.
 *
 * Hạng quyết định hội thoại trong dự án có được chọn model Premium hay không — chủ và mọi thành viên
 * của dự án dùng chung quyền đó. Đổi hạng được lại bất cứ lúc nào nên không đòi gõ xác nhận như xoá
 * tài khoản; hạ về Free có hiệu lực từ câu hỏi kế tiếp của người dùng.
 */

import { useEffect, useState } from 'react';

import { AdminTabs } from '@/features/admin/components/AdminTabs';
import { useAdminProjects, useChangeProjectTier } from '@/features/admin/hooks/useAdminProjects';
import { ApiError } from '@/shared/lib/apiClient';
import { IconSearch } from '@/shared/ui/icons';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/states';
import { buttonClass, CARD, INPUT } from '@/shared/ui/styles';

import type { AdminProject } from '@/features/admin/api/adminApi';

const SEARCH_DEBOUNCE_MS = 300;

export function AdminProjectsPage() {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');

  // Chờ người dùng ngừng gõ rồi mới gọi backend: mỗi phím một request là phí và làm bảng nhảy.
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [search]);

  const projects = useAdminProjects(query);
  const changeTier = useChangeProjectTier();

  return (
    <section className="flex flex-col gap-4">
      <AdminTabs />
      <PageHeader
        title="Quản lý dự án"
        description="Dự án Premium cho chủ và mọi thành viên chọn model Claude Sonnet ở từng câu hỏi; dự án Free chỉ dùng Gemini miễn phí."
      />

      <div className={`${CARD} flex items-center gap-2 px-3 py-2.5`}>
        <IconSearch className="text-ink-subtle size-4" />
        <label htmlFor="admin-project-search" className="sr-only">
          Tìm dự án theo tên hoặc email chủ dự án
        </label>
        <input
          id="admin-project-search"
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Tìm theo tên dự án hoặc email chủ dự án…"
          autoComplete="off"
          className={`${INPUT} max-w-md`}
        />
        {projects.isSuccess ? (
          <span className="text-ink-subtle ml-auto text-xs">{projects.data.length} dự án</span>
        ) : null}
      </div>

      {changeTier.isError ? (
        <p role="alert" className="text-danger m-0 text-sm">
          {changeTier.error instanceof ApiError
            ? changeTier.error.message
            : 'Không đổi được hạng dự án.'}
        </p>
      ) : null}

      {projects.isPending ? <LoadingState label="Đang tải danh sách dự án…" shape="table" /> : null}
      {projects.isError ? (
        <ErrorState
          title="Không tải được danh sách dự án."
          message={projects.error instanceof ApiError ? projects.error.message : undefined}
          hint="Kiểm tra trạng thái backend ở chân trang rồi tải lại trang."
          onRetry={() => void projects.refetch()}
        />
      ) : null}
      {projects.isSuccess && projects.data.length === 0 ? (
        <div className={CARD}>
          <EmptyState
            icon={<IconSearch className="size-5" />}
            title="Không có dự án nào khớp."
            description="Tìm theo một phần tên dự án hoặc email chủ dự án, không phân biệt hoa thường."
          />
        </div>
      ) : null}
      {projects.isSuccess && projects.data.length > 0 ? (
        <div className={`${CARD} overflow-x-auto`}>
          <table className="w-full min-w-[56rem] border-collapse text-sm">
            <thead>
              <tr className="border-border text-ink-subtle text-2xs border-b text-left font-medium tracking-wider uppercase">
                <th className="py-2 pr-3 pl-3 font-medium">Dự án</th>
                <th className="py-2 pr-3 font-medium">Chủ dự án</th>
                <th className="py-2 pr-3 font-medium">Hạng</th>
                <th className="py-2 pr-3 text-right font-medium">Thành viên</th>
                <th className="py-2 pr-3 text-right font-medium">Snapshot</th>
                <th className="py-2 pr-3 text-right font-medium">Hội thoại</th>
                <th className="py-2 pr-3 font-medium">
                  <span className="sr-only">Thao tác</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-border divide-y">
              {projects.data.map((project) => (
                <ProjectRow
                  key={project.id}
                  project={project}
                  pending={changeTier.isPending && changeTier.variables?.projectId === project.id}
                  onChange={() =>
                    changeTier.mutate({
                      projectId: project.id,
                      tier: project.tier === 'PREMIUM' ? 'FREE' : 'PREMIUM',
                    })
                  }
                />
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

interface ProjectRowProps {
  project: AdminProject;
  pending: boolean;
  onChange: () => void;
}

function ProjectRow({ project, pending, onChange }: ProjectRowProps) {
  const premium = project.tier === 'PREMIUM';
  return (
    <tr className="hover:bg-surface-sunken align-top">
      <td className="py-2 pr-3 pl-3">
        <span className="text-ink block font-medium">{project.name}</span>
        <span className="text-ink-subtle block text-xs tabular-nums">
          Tạo ngày {new Date(project.createdAt).toLocaleDateString('vi-VN')}
        </span>
      </td>
      <td className="py-2 pr-3">
        <span className="text-ink block">{project.ownerDisplayName}</span>
        <span className="text-ink-muted block font-mono text-xs break-all">
          {project.ownerEmail}
        </span>
      </td>
      <td className="py-2 pr-3">
        <StatusBadge tone={premium ? 'ok' : 'neutral'} label={premium ? 'Premium' : 'Free'} />
      </td>
      <td className="text-ink py-2 pr-3 text-right tabular-nums">{project.memberCount}</td>
      <td className="text-ink py-2 pr-3 text-right tabular-nums">{project.snapshotCount}</td>
      <td className="text-ink py-2 pr-3 text-right tabular-nums">{project.conversationCount}</td>
      <td className="py-2 pr-3 text-right">
        <button
          type="button"
          onClick={onChange}
          disabled={pending}
          aria-label={`${premium ? 'Hạ về Free' : 'Nâng lên Premium'}: ${project.name}`}
          className={buttonClass(premium ? 'secondary' : 'primary', 'sm')}
        >
          {premium ? 'Hạ về Free' : 'Nâng lên Premium'}
        </button>
      </td>
    </tr>
  );
}

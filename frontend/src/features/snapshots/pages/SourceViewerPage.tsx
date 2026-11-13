/**
 * Xem nội dung một file trong snapshot.
 *
 * ==================== QUYẾT ĐỊNH: KHÔNG ẢO HOÁ DANH SÁCH DÒNG ====================
 * Ảo hoá (chỉ render các dòng đang nhìn thấy) là phản xạ thường gặp cho danh sách dài, nhưng ở đây
 * nó phá đúng những thứ người xem mã nguồn cần nhất:
 *   1. `Ctrl+F` của trình duyệt không tìm được nội dung chưa render — mất chức năng tìm kiếm.
 *   2. `#L42` không cuộn tới được, vì phần tử `L42` chưa tồn tại trong DOM.
 *   3. Bôi đen nhiều dòng rồi copy chỉ lấy được phần đang hiển thị — mất dữ liệu im lặng.
 * Thay vào đó, backend cắt lát sẵn (tối đa 2.000 dòng, xem `SnapshotQueryService`) và trả cờ
 * `truncated` để hiện banner. Nếu sau này thật sự cần ảo hoá, lối thoát là `@tanstack/react-virtual`
 * — nhưng phải giải quyết cả ba điểm trên trước.
 * ================================================================================
 */

import { useParams } from 'react-router-dom';

import { CodeLine } from '@/features/snapshots/components/CodeLine';
import { useFileContent } from '@/features/snapshots/hooks/useSnapshots';
import { useLineTarget } from '@/features/snapshots/hooks/useLineTarget';
import { fromRouteParam } from '@/features/snapshots/lib/filePathUrl';
import { ApiError } from '@/shared/lib/apiClient';
import { FullPageSpinner } from '@/shared/ui/FullPageSpinner';
import { IconCode, IconFile } from '@/shared/ui/icons';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { CARD } from '@/shared/ui/styles';

export function SourceViewerPage() {
  const params = useParams<{ snapshotId: string; '*': string }>();
  const snapshotId = params.snapshotId ?? '';
  const path = fromRouteParam(params['*']);

  const { data, isPending, isError, error } = useFileContent(snapshotId, path);
  // Khoảng, không phải một dòng: một bằng chứng thường trải trên cả method (`#L37-L44`), và tô
  // đúng một dòng trong đó thì người đọc không thấy được phạm vi mà cảnh báo nói tới.
  const targetRange = useLineTarget(data !== undefined);

  if (path === '') {
    return (
      <div
        className={`${CARD} text-ink-muted flex flex-col items-center px-6 py-16 text-center text-sm`}
      >
        <span className="bg-surface-sunken mb-3 grid size-11 place-items-center rounded-xl">
          <IconCode className="size-5" />
        </span>
        <p className="m-0">Chọn một file ở cây bên trái để xem nội dung.</p>
      </div>
    );
  }

  if (isPending) {
    return <FullPageSpinner label="Đang tải nội dung file…" />;
  }

  if (isError) {
    return <FileError error={error} />;
  }

  return (
    <article className={`${CARD} min-w-0 overflow-hidden`}>
      <header className="border-border bg-surface-muted flex flex-wrap items-center justify-between gap-2 border-b px-4 py-2.5">
        <h2 className="text-ink m-0 flex min-w-0 items-center gap-2 font-mono text-[13px] font-medium break-all">
          <IconFile className="text-ink-muted size-4" />
          {path}
        </h2>
        <span className="text-ink-muted text-xs">{data.totalLines} dòng</span>
      </header>

      {data.truncated ? (
        <p className="border-border m-0 border-b bg-amber-50 px-4 py-2 text-sm text-amber-800">
          Đang hiển thị dòng {data.fromLine}–{data.toLine} trên tổng {data.totalLines} dòng.
        </p>
      ) : null}

      <div className="bg-code-bg overflow-x-auto py-2 font-mono text-[13px] leading-6">
        {data.lines.map((text, index) => {
          // Số dòng thật = dòng bắt đầu của lát + vị trí trong lát. Dùng chỉ số mảng làm số dòng là
          // sai ngay khi người dùng mở một khoảng dòng bắt đầu từ giữa file.
          const lineNumber = data.fromLine + index;
          return (
            <CodeLine
              key={lineNumber}
              lineNumber={lineNumber}
              text={text}
              highlighted={
                targetRange !== null &&
                lineNumber >= targetRange.fromLine &&
                lineNumber <= targetRange.toLine
              }
            />
          );
        })}
      </div>
    </article>
  );
}

function FileError({ error }: { error: unknown }) {
  if (!(error instanceof ApiError)) {
    return <StatusBadge tone="danger" label="Không đọc được nội dung file." />;
  }

  const type = error.problem.type ?? '';
  if (type.endsWith('binary-file')) {
    return <StatusBadge tone="neutral" label="File nhị phân — không hiển thị theo dòng được." />;
  }
  if (type.endsWith('file-content-not-stored')) {
    return (
      <StatusBadge
        tone="warning"
        label="File này vượt ngưỡng lưu trữ nên chỉ được lập chỉ mục, không có nội dung để đọc."
      />
    );
  }
  if (error.status === 404) {
    return <StatusBadge tone="danger" label="Không tìm thấy file này trong snapshot." />;
  }
  return <StatusBadge tone="danger" label={error.problem.detail ?? error.message} />;
}

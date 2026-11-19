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

import { useMemo } from 'react';
import { useParams } from 'react-router-dom';

import { CodeLine } from '@/features/snapshots/components/CodeLine';
import { useFileContent } from '@/features/snapshots/hooks/useSnapshots';
import { useLineTarget } from '@/features/snapshots/hooks/useLineTarget';
import { highlightLines, languageOf } from '@/features/snapshots/lib/highlight';
import { fromRouteParam } from '@/features/snapshots/lib/filePathUrl';
import { ApiError } from '@/shared/lib/apiClient';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/states';
import { IconCode, IconFile, IconInfo } from '@/shared/ui/icons';
import { CARD } from '@/shared/ui/styles';

export function SourceViewerPage() {
  const params = useParams<{ snapshotId: string; '*': string }>();
  const snapshotId = params.snapshotId ?? '';
  const path = fromRouteParam(params['*']);

  const { data, isPending, isError, error } = useFileContent(snapshotId, path);
  // Khoảng, không phải một dòng: một bằng chứng thường trải trên cả method (`#L37-L44`), và tô
  // đúng một dòng trong đó thì người đọc không thấy được phạm vi mà cảnh báo nói tới.
  const targetRange = useLineTarget(data !== undefined);
  // Tô màu CẢ lát một lần rồi chia dòng (comment khối trải nhiều dòng), và chỉ tính lại khi đổi
  // file — không tính lại mỗi lần render vì cuộn hay đổi dòng đang tô.
  const segmentsByLine = useMemo(
    () => (data === undefined ? [] : highlightLines(data.lines, languageOf(path))),
    [data, path],
  );

  if (path === '') {
    return (
      <div className={CARD}>
        <EmptyState
          icon={<IconCode className="size-5" />}
          title="Chọn một file ở cây bên trái để xem nội dung."
          description="Hoặc mở danh sách cảnh báo — bấm vào vị trí của một cảnh báo là nhảy thẳng tới đúng dòng."
        />
      </div>
    );
  }

  if (isPending) {
    return <LoadingState label="Đang tải nội dung file…" shape="code" rows={18} />;
  }

  if (isError) {
    return <FileError error={error} />;
  }

  return (
    <article className={`${CARD} min-w-0 overflow-hidden`}>
      <header className="border-border bg-surface flex flex-wrap items-center justify-between gap-2 border-b px-3 py-2">
        <h2 className="text-ink m-0 flex min-w-0 items-center gap-2 font-mono text-sm font-medium break-all">
          <IconFile className="text-ink-muted size-4" />
          {path}
        </h2>
        <span className="text-ink-subtle text-xs">{data.totalLines} dòng</span>
      </header>

      {data.truncated ? (
        <p className="border-border bg-warning/5 text-warning m-0 border-b px-3 py-1.5 text-xs">
          Đang hiển thị dòng {data.fromLine}–{data.toLine} trên tổng {data.totalLines} dòng.
        </p>
      ) : null}

      <div className="bg-code-bg overflow-x-auto font-mono text-sm leading-5">
        {data.lines.map((text, index) => {
          // Số dòng thật = dòng bắt đầu của lát + vị trí trong lát. Dùng chỉ số mảng làm số dòng là
          // sai ngay khi người dùng mở một khoảng dòng bắt đầu từ giữa file.
          const lineNumber = data.fromLine + index;
          return (
            <CodeLine
              key={lineNumber}
              lineNumber={lineNumber}
              text={text}
              segments={segmentsByLine[index]}
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
    return (
      <ErrorState
        title="Không đọc được nội dung file."
        hint="Kiểm tra trạng thái backend ở chân trang rồi tải lại trang."
      />
    );
  }

  // Hai ca dưới KHÔNG phải lỗi — file tồn tại, chỉ là không có nội dung dạng dòng để hiện.
  const type = error.problem.type ?? '';
  if (type.endsWith('binary-file')) {
    return (
      <div className={CARD}>
        <EmptyState
          icon={<IconFile className="size-5" />}
          title="File nhị phân — không hiển thị theo dòng được."
          description="Analyzer không quét file nhị phân, nên không có cảnh báo nào trỏ vào đây."
        />
      </div>
    );
  }
  if (type.endsWith('file-content-not-stored')) {
    return (
      <div className={CARD}>
        <EmptyState
          icon={<IconInfo className="size-5" />}
          title="File này vượt ngưỡng lưu trữ nên chỉ được lập chỉ mục, không có nội dung để đọc."
          description="Ngưỡng do UPLOAD_MAX_STORED_FILE_BYTES của core-backend quyết định (mặc định 2 MB)."
        />
      </div>
    );
  }
  if (error.status === 404) {
    return (
      <ErrorState
        title="Không tìm thấy file này trong snapshot."
        hint="Đường dẫn trong URL có thể đã đổi — chọn lại file từ cây bên trái."
      />
    );
  }
  return (
    <ErrorState
      title="Không đọc được nội dung file."
      message={error.problem.detail ?? error.message}
    />
  );
}

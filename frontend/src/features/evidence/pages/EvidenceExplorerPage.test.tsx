/**
 * Evidence Explorer. Render qua `MemoryRouter` với đúng hình dạng route thật để tham số
 * `:snapshotId/:runId/:shortId` được phân giải y như trong ứng dụng.
 *
 * Ca quan trọng nhất KHÔNG phải "hiện đủ dữ liệu" mà là ba trạng thái rỗng/lỗi/đang tải phân biệt
 * được với nhau: gộp chúng lại là phá đúng thứ mà backend cố tình trả 409 (thay vì mảng rỗng) để
 * bảo toàn.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import type { EvidenceDetail } from '@/features/evidence/api/evidenceApi';
import { EvidenceExplorerPage } from '@/features/evidence/pages/EvidenceExplorerPage';
import { ApiError } from '@/shared/lib/apiClient';
import { createTestQueryClient } from '@/shared/lib/queryClient';

const getEvidenceDetailMock = vi.hoisted(() => vi.fn());

vi.mock('@/features/evidence/api/evidenceApi', () => ({
  getEvidenceDetail: getEvidenceDetailMock,
}));

/**
 * `ReviewPanel` nằm ở cuối trang nhưng vẫn render cùng lượt nên nó cũng gọi API. Không mock thì
 * `fetch` thật bay ra ngoài (`ENOTFOUND api.test.local`) — test vẫn xanh vì panel chỉ hiện "chưa
 * đánh giá", nhưng đó đúng là loại rò rỉ mà bộ test này phải chặn. Hành vi của panel có bộ test
 * riêng ở `features/review`.
 */
vi.mock('@/features/review/api/reviewApi', () => ({
  listReviews: () => Promise.resolve([]),
  submitReview: () => Promise.reject(new Error('không dùng trong test này')),
  getRunMetrics: () => Promise.reject(new Error('không dùng trong test này')),
}));

function baseDetail(overrides: Partial<EvidenceDetail> = {}): EvidenceDetail {
  return {
    evidenceId: 'uuid-1',
    shortId: 'EV-005A',
    analysisRunId: 'r1',
    snapshotId: 's1',
    type: 'STATIC_WARNING',
    source: 'spotbugs',
    summary: 'MD5 duoc dung de bam mat khau',
    filePath: 'src/main/java/PasswordHasher.java',
    startLine: 40,
    endLine: 40,
    codeContext: [],
    tests: [],
    coverage: [],
    other: [],
    relatedTotal: 0,
    ...overrides,
  };
}

function renderExplorer() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={['/snapshots/s1/runs/r1/evidence/EV-005A']}>
        <Routes>
          <Route
            path="/snapshots/:snapshotId/runs/:runId/evidence/:shortId"
            element={<EvidenceExplorerPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('EvidenceExplorerPage', () => {
  /** Chính là deliverable của tuần 10-11. */
  it('mo mot canh bao thi thay ngu canh ma, test lien quan va do phu', async () => {
    getEvidenceDetailMock.mockResolvedValue(
      baseDetail({
        relatedTotal: 3,
        codeContext: [
          {
            evidenceId: 'c1',
            shortId: 'EV-0042',
            type: 'CODE_CONTEXT',
            source: 'parser',
            summary: 'Ngu canh PasswordHasher#hashPassword',
            relation: 'LOCATED_IN',
            method: 'LINE_EXACT',
            weight: 1,
            outgoing: true,
          },
        ],
        tests: [
          {
            evidenceId: 't1',
            shortId: 'EV-0103',
            type: 'FAILED_TEST',
            source: 'surefire',
            summary: 'Test hong PasswordHasherTest',
            relation: 'SUPPORTS',
            method: 'STACK_FRAME',
            weight: 1,
            outgoing: false,
            testCase: {
              className: 'PasswordHasherTest',
              methodName: 'md5KhongDuocDungChoMatKhau',
              status: 'FAILED',
              failureMessage: 'expected: <true> but was: <false>',
            },
          },
        ],
        coverage: [
          {
            evidenceId: 'v1',
            shortId: 'EV-0210',
            type: 'COVERAGE_INFORMATION',
            source: 'jacoco',
            summary: 'Do phu PasswordHasher.java',
            relation: 'COVERS',
            method: 'LINE_EXACT',
            weight: 1,
            outgoing: false,
          },
        ],
      }),
    );

    renderExplorer();

    expect(await screen.findByText('Ngu canh PasswordHasher#hashPassword')).toBeInTheDocument();
    expect(screen.getByText('Do phu PasswordHasher.java')).toBeInTheDocument();
    // Thẻ test KHÔNG hiện câu tóm tắt (nó lặp lại khối có cấu trúc) — xem
    // `RelatedEvidenceCard.test.tsx`. Nhận nó qua chính trường có cấu trúc đó.
    expect(screen.getByText(/PasswordHasherTest#md5KhongDuocDungChoMatKhau/)).toBeInTheDocument();
    expect(screen.getByText(/expected: <true> but was: <false>/)).toBeInTheDocument();
  });

  /**
   * Panel đánh giá phải nằm SAU phần bằng chứng trong DOM.
   *
   * Đây là quyết định về **giao thức user study**, không phải sở thích bố cục: bốn nút quyết định
   * đặt trên đầu là lời mời bấm trước khi cuộn xuống đọc, mà "người dùng quyết định SAU KHI xem
   * bằng chứng" chính là thứ tuần 15-16 đo. Một lần "dọn lại bố cục cho gọn" là đủ để đảo nó về
   * mà không ai nhận ra, nên ghim bằng test.
   */
  it('panel danh gia nam SAU phan bang chung', async () => {
    getEvidenceDetailMock.mockResolvedValue(
      baseDetail({
        relatedTotal: 1,
        tests: [
          {
            evidenceId: 't1',
            shortId: 'EV-0103',
            type: 'FAILED_TEST',
            source: 'surefire',
            summary: 'Test hong PasswordHasherTest',
            relation: 'SUPPORTS',
            method: 'STACK_FRAME',
            weight: 1,
            outgoing: false,
          },
        ],
      }),
    );

    renderExplorer();

    // Tiêu đề nhóm mang sẵn bộ đếm ("Test liên quan (1)") nên khớp bằng regex.
    const bangChung = await screen.findByRole('heading', { name: /Test liên quan/ });
    const panel = screen.getByRole('heading', { name: 'Đánh giá của bạn' });

    // DOCUMENT_POSITION_FOLLOWING: `panel` đứng SAU `bangChung` trong thứ tự tài liệu.
    expect(
      bangChung.compareDocumentPosition(panel) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  /**
   * `method` và `weight` phải hiện ra. Nếu ai đó "làm gọn giao diện" bằng cách giấu chúng, test
   * này đỏ — vì tuần 15 phải đo được tỉ lệ liên kết chỉ dựa vào quy ước tên.
   */
  it('hien method va weight, va danh dau lien ket yeu', async () => {
    getEvidenceDetailMock.mockResolvedValue(
      baseDetail({
        relatedTotal: 1,
        tests: [
          {
            evidenceId: 't1',
            shortId: 'EV-0103',
            type: 'TEST_RESULT',
            source: 'surefire',
            summary: 'Test xanh',
            relation: 'CONTRADICTS',
            method: 'NAMING_CONVENTION',
            weight: 0.25,
            outgoing: false,
            rationale: 'Test xanh phu dong nay. KHONG chung minh canh bao sai.',
          },
        ],
      }),
    );

    renderExplorer();

    expect(await screen.findByText('NAMING_CONVENTION')).toBeInTheDocument();
    expect(screen.getByText(/0\.25/)).toBeInTheDocument();
    expect(screen.getByText(/liên kết yếu/)).toBeInTheDocument();
    // rationale mang câu cảnh báo cạm bẫy của vulnshop — không được nuốt.
    expect(screen.getByText(/KHONG chung minh canh bao sai/)).toBeInTheDocument();
  });

  /** "Không có bằng chứng" là một KẾT QUẢ, không phải lỗi và không phải đang tải. */
  it('khong co lien ket nao thi hien thong bao co nghia, khong phai loi', async () => {
    getEvidenceDetailMock.mockResolvedValue(baseDetail({ relatedTotal: 0 }));

    renderExplorer();

    expect(await screen.findByText(/Không có bằng chứng nào liên kết/)).toBeInTheDocument();
    expect(screen.getByText(/INSUFFICIENT_EVIDENCE/)).toBeInTheDocument();
    // Ba trạng thái không được lẫn nhau.
    expect(screen.queryByText(/Đang tải/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Không tải được/)).not.toBeInTheDocument();
  });

  /** Rỗng vì chuẩn hoá đường dẫn hỏng là một câu KHÁC với rỗng vì linker không tìm thấy. */
  it('khong doi chieu duoc duong dan thi noi ro ly do do', async () => {
    getEvidenceDetailMock.mockResolvedValue(
      baseDetail({ relatedTotal: 0, filePath: undefined, startLine: undefined }),
    );

    renderExplorer();

    expect(await screen.findByText(/không có điểm neo để chạy/)).toBeInTheDocument();
  });

  it('run chua hoan tat thi noi rieng, khong lan voi "khong co bang chung"', async () => {
    getEvidenceDetailMock.mockRejectedValue(
      new ApiError(409, { type: 'https://datn.hust.vn/problems/run-not-queryable' }),
    );

    renderExplorer();

    expect(await screen.findByText(/chưa hoàn tất/)).toBeInTheDocument();
    expect(screen.queryByText(/Không có bằng chứng nào liên kết/)).not.toBeInTheDocument();
  });

  /**
   * Hai nhánh 404 phải cho hai câu KHÁC NHAU — cùng hai giá trị `ClaimCitationResolution` mà
   * CitationValidator dùng lại ở tuần 12-13.
   */
  it('phan biet evidence-not-found voi evidence-wrong-run', async () => {
    getEvidenceDetailMock.mockRejectedValue(
      new ApiError(404, { type: 'https://datn.hust.vn/problems/evidence-wrong-run' }),
    );
    renderExplorer();
    expect(await screen.findByText(/thuộc một lần phân tích khác/)).toBeInTheDocument();

    vi.clearAllMocks();
    getEvidenceDetailMock.mockRejectedValue(
      new ApiError(404, { type: 'https://datn.hust.vn/problems/evidence-not-found' }),
    );
    renderExplorer();
    expect(await screen.findByText(/Không tìm thấy Evidence ID/)).toBeInTheDocument();
  });

  /** Deep-link tới nguồn phải mang khoảng dòng, để tuần 12-13 citation dùng lại được cơ chế này. */
  it('link toi ma nguon mang khoang dong', async () => {
    getEvidenceDetailMock.mockResolvedValue(
      baseDetail({ startLine: 37, endLine: 44, relatedTotal: 0 }),
    );

    const { container } = renderExplorer();

    await screen.findByText(/MD5 duoc dung/);
    const link = container.querySelector('a[href*="#L37-L44"]');
    expect(link).not.toBeNull();
  });
});

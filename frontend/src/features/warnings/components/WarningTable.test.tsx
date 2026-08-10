/**
 * Chốt chặn chính của màn hình cảnh báo: hàng KHÔNG đối chiếu được đường dẫn.
 *
 * Đó là thất bại chuẩn hoá path — thứ ADR 0004 gọi là rủi ro lớn nhất còn lại. Nếu bảng lặng lẽ
 * bỏ qua hàng đó, hoặc render nó thành một link chết, thì thất bại quay lại thành hỏng-im-lặng
 * đúng như trước khi có `unresolved_location_count`.
 */

import { describe, expect, it } from 'vitest';

import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import { WarningTable } from '@/features/warnings/components/WarningTable';

import type { ReviewDecision } from '@/features/review/api/reviewApi';
import type { Warning } from '@/features/warnings/api/warningApi';

function warning(overrides: Partial<Warning> = {}): Warning {
  return {
    evidenceId: '11111111-1111-1111-1111-111111111111',
    shortId: 'EV-001A',
    analysisRunId: '22222222-2222-2222-2222-222222222222',
    filePath: 'src/main/java/vn/hust/App.java',
    startLine: 42,
    summary: 'Mat khau bam bang MD5',
    tool: 'spotbugs',
    ruleId: 'WEAK_MESSAGE_DIGEST_MD5',
    ruleIdNormalized: 'WEAK_MESSAGE_DIGEST_MD5',
    severityNormalized: 'HIGH',
    hasPrimaryLocation: true,
    ...overrides,
  };
}

function renderTable(warnings: Warning[], reviewByShortId?: Map<string, ReviewDecision>) {
  return render(
    <MemoryRouter>
      <WarningTable
        warnings={warnings}
        snapshotId="snap-1"
        runId="run-1"
        reviewByShortId={reviewByShortId}
      />
    </MemoryRouter>,
  );
}

describe('WarningTable', () => {
  /**
   * `shortId` là lối vào Evidence Explorer, và nó vá một lỗ hổng sẵn có: cảnh báo không đối chiếu
   * được đường dẫn thì cột Vị trí không render link, nên trước đây nó HOÀN TOÀN không bấm được.
   */
  it('shortId là link mở Evidence Explorer', () => {
    renderTable([warning()]);

    expect(screen.getByRole('link', { name: 'EV-001A' })).toHaveAttribute(
      'href',
      '/snapshots/snap-1/runs/run-1/evidence/EV-001A',
    );
  });

  it('cảnh báo không đối chiếu được đường dẫn vẫn bấm được qua shortId', () => {
    renderTable([warning({ filePath: undefined, startLine: undefined })]);

    expect(screen.getByRole('link', { name: 'EV-001A' })).toBeInTheDocument();
  });

  it('bấm vào vị trí thì nhảy tới đúng file và đúng dòng của source viewer', () => {
    renderTable([warning()]);

    const link = screen.getByRole('link', { name: /App\.java:42/ });
    expect(link).toHaveAttribute(
      'href',
      '/snapshots/snap-1/files/src/main/java/vn/hust/App.java#L42',
    );
  });

  /**
   * `encodeURIComponent` cả chuỗi biến `/` thành `%2F` và phá splat route `files/*`. Dùng
   * `toFilePathUrl` (mã hoá TỪNG đoạn) thì dấu `/` giữ nguyên còn ký tự lạ vẫn được mã hoá.
   */
  it('mã hoá theo từng đoạn nên đường dẫn có ký tự lạ vẫn giữ nguyên cấu trúc thư mục', () => {
    renderTable([warning({ filePath: 'src/bao cao/100%.java', startLine: 7 })]);

    expect(screen.getByRole('link', { name: /100%\.java:7/ })).toHaveAttribute(
      'href',
      '/snapshots/snap-1/files/src/bao%20cao/100%25.java#L7',
    );
  });

  /**
   * Ô vị trí render thư mục và tên file thành hai đoạn để dòng ngắt ở chỗ có nghĩa. Chia như vậy
   * rất dễ kéo theo cám dỗ cắt bớt cho gọn — nhưng đường dẫn là thứ người dùng đối chiếu với mã
   * nguồn, mất một đoạn giữa là mất luôn khả năng kiểm chứng. Test này chốt: đủ từng ký tự.
   */
  it('hiện đủ đường dẫn dù chia thành hai đoạn để xuống dòng', () => {
    renderTable([warning()]);

    expect(screen.getByRole('link', { name: /App\.java:42/ }).textContent).toBe(
      'src/main/java/vn/hust/App.java:42',
    );
  });

  it('hàng không đối chiếu được đường dẫn vẫn hiện ra nhưng VỊ TRÍ không bấm được', () => {
    renderTable([warning({ filePath: undefined, startLine: undefined, shortId: 'EV-002B' })]);

    // Bằng chứng không bao giờ bị giấu đi.
    expect(screen.getByText('Mat khau bam bang MD5')).toBeInTheDocument();
    // Lý do không bấm được phải hiện thành chữ, không phải một link chết.
    expect(screen.getByText(/chưa đối chiếu được với snapshot/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /App\.java/ })).not.toBeInTheDocument();
    // Nhưng bằng chứng vẫn mở được qua shortId — xem ca đầu tiên của bộ test này.
    expect(screen.getByRole('link', { name: 'EV-002B' })).toBeInTheDocument();
  });

  it('cảnh báo không có số dòng vẫn bấm được, chỉ là không có hash', () => {
    renderTable([warning({ startLine: undefined })]);

    expect(screen.getByRole('link', { name: /App\.java/ })).toHaveAttribute(
      'href',
      '/snapshots/snap-1/files/src/main/java/vn/hust/App.java',
    );
  });

  /**
   * Cột Đánh giá phải phân biệt được "chưa ai xét" với các quyết định — đó là tiến độ review, và
   * nó là mẫu số của Human acceptance rate. Một ô trống thì người dùng không biết mình còn nợ bao
   * nhiêu cảnh báo.
   */
  it('cột Đánh giá hiện quyết định đã có và dấu gạch cho cảnh báo chưa xét', () => {
    renderTable(
      [warning(), warning({ evidenceId: 'x', shortId: 'EV-003C' })],
      new Map([['EV-001A', 'REJECT']]),
    );

    expect(screen.getByText('Báo nhầm')).toBeInTheDocument();
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  /**
   * `UNCERTAIN` và `INSUFFICIENT_EVIDENCE` cùng là "chưa quyết" nhưng nói hai chuyện khác nhau:
   * cái đầu về người, cái sau về hệ thống. Gộp nhãn là gộp hai tín hiệu cải tiến khác nhau.
   */
  it('phân biệt "chưa quyết được" với "thiếu bằng chứng"', () => {
    renderTable(
      [warning(), warning({ evidenceId: 'x', shortId: 'EV-003C' })],
      new Map<string, ReviewDecision>([
        ['EV-001A', 'UNCERTAIN'],
        ['EV-003C', 'INSUFFICIENT_EVIDENCE'],
      ]),
    );

    expect(screen.getByText('Chưa quyết')).toBeInTheDocument();
    expect(screen.getByText('Thiếu bằng chứng')).toBeInTheDocument();
  });

  it('hiện nhãn mức nghiêm trọng của từng hàng', () => {
    renderTable([
      warning({ severityNormalized: 'HIGH' }),
      warning({ evidenceId: 'x', shortId: 'EV-003C', severityNormalized: 'LOW' }),
    ]);

    expect(screen.getByText('HIGH')).toBeInTheDocument();
    expect(screen.getByText('LOW')).toBeInTheDocument();
  });
});

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

function renderTable(warnings: Warning[]) {
  return render(
    <MemoryRouter>
      <WarningTable warnings={warnings} snapshotId="snap-1" />
    </MemoryRouter>,
  );
}

describe('WarningTable', () => {
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

    expect(screen.getByRole('link').textContent).toBe('src/main/java/vn/hust/App.java:42');
  });

  it('hàng không đối chiếu được đường dẫn vẫn hiện ra nhưng KHÔNG bấm được', () => {
    renderTable([warning({ filePath: undefined, startLine: undefined, shortId: 'EV-002B' })]);

    // Bằng chứng không bao giờ bị giấu đi.
    expect(screen.getByText('EV-002B')).toBeInTheDocument();
    expect(screen.getByText('Mat khau bam bang MD5')).toBeInTheDocument();
    // Nhưng lý do không bấm được phải hiện thành chữ, không phải một link chết.
    expect(screen.getByText(/chưa đối chiếu được với snapshot/)).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('cảnh báo không có số dòng vẫn bấm được, chỉ là không có hash', () => {
    renderTable([warning({ startLine: undefined })]);

    expect(screen.getByRole('link')).toHaveAttribute(
      'href',
      '/snapshots/snap-1/files/src/main/java/vn/hust/App.java',
    );
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

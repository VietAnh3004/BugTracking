/**
 * Thẻ bằng chứng liên quan phải chịu được `null` ở MỌI trường tuỳ chọn.
 *
 * Bộ test này sinh ra sau một lỗi thật làm **trắng nguyên trang** Evidence Explorer:
 * `TypeError: Cannot read properties of null (reading 'className')`.
 *
 * Gốc rễ là lệch hợp đồng: `openapi-typescript` sinh `testCase?: TestCaseView` — tức
 * `TestCaseView | undefined`, **không có `null`** — trong khi Jackson mặc định serialize
 * `"testCase": null`. Nên `item.testCase === undefined` là `false`, nhánh render chạy, và
 * `null.className` nổ. TypeScript hoàn toàn không thấy: mã đó biên dịch sạch.
 *
 * Nay backend bỏ hẳn trường null (`jackson.default-property-inclusion: non_null`), nhưng bộ test
 * này cố ý dựng dữ liệu kiểu CŨ: nó là chốt chặn nếu cấu hình đó bị gỡ, hoặc nếu một endpoint nào
 * đó tự serialize null theo đường khác. Ép kiểu ở đây là chủ ý — mô phỏng dữ liệu thật, không
 * phải dữ liệu mà trình biên dịch tin là có thể.
 */

import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { RelatedEvidenceCard } from '@/features/evidence/components/RelatedEvidenceCard';

import type { RelatedEvidence } from '@/features/evidence/api/evidenceApi';

/** Đúng hình dạng backend gửi TRƯỚC bản vá: trường vắng mặt được thay bằng `null`. */
function itemVoiNull(overrides: Record<string, unknown> = {}): RelatedEvidence {
  return {
    evidenceId: '11111111-1111-1111-1111-111111111111',
    shortId: 'EV-0779',
    type: 'CODE_CONTEXT',
    source: 'parser',
    summary: 'Ngu canh ma nguon UserService#displayNameOf',
    relation: 'LOCATED_IN',
    method: 'LINE_EXACT',
    weight: 1,
    testCase: null,
    rationale: null,
    filePath: null,
    startLine: null,
    endLine: null,
    symbol: null,
    ...overrides,
  } as unknown as RelatedEvidence;
}

function renderCard(item: RelatedEvidence) {
  return render(
    <MemoryRouter>
      <RelatedEvidenceCard item={item} snapshotId="snap-1" runId="run-1" />
    </MemoryRouter>,
  );
}

describe('RelatedEvidenceCard', () => {
  /** Chính là ca làm trắng trang. */
  it('testCase là null thì vẫn render được, không nổ TypeError', () => {
    renderCard(itemVoiNull());

    expect(screen.getByRole('link', { name: 'EV-0779' })).toBeInTheDocument();
    expect(screen.getByText(/Ngu canh ma nguon/)).toBeInTheDocument();
    // Khối thông tin test phải VẮNG hẳn, không phải hiện rỗng.
    expect(screen.queryByText('Test:')).not.toBeInTheDocument();
  });

  /** `filePath` null mà lọt vào `toFilePathUrl` cũng là một TypeError nữa, chỉ khác dòng. */
  it('filePath là null thì không dựng link tới mã nguồn', () => {
    renderCard(itemVoiNull());

    expect(screen.queryByText(/↗/)).not.toBeInTheDocument();
  });

  it('rationale là null thì không hiện dòng giải thích rỗng', () => {
    const { container } = renderCard(itemVoiNull());

    expect(container.querySelector('p.italic')).toBeNull();
  });

  /** Có dữ liệu thì vẫn phải hiện đủ — bản vá không được im lặng nuốt nội dung thật. */
  it('có testCase và filePath thì hiện đủ cả hai', () => {
    renderCard(
      itemVoiNull({
        testCase: {
          className: 'vn.hust.datn.casestudy.vulnshop.user.UserServiceTest',
          methodName: 'displayNameOf_khongTonTai_khongDuocNemNPE',
          status: 'ERROR',
          failureMessage: 'java.lang.NullPointerException',
        },
        filePath: 'src/main/java/vn/hust/datn/casestudy/vulnshop/user/UserService.java',
        startLine: 66,
        rationale: 'Stack trace cua test hong di qua dong 66',
      }),
    );

    expect(screen.getByText(/UserServiceTest#displayNameOf_khongTonTai/)).toBeInTheDocument();
    expect(screen.getByText('ERROR')).toBeInTheDocument();
    expect(screen.getByText(/java\.lang\.NullPointerException/)).toBeInTheDocument();
    expect(screen.getByText(/Stack trace cua test hong/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /UserService\.java:66/ })).toHaveAttribute(
      'href',
      '/snapshots/snap-1/files/src/main/java/vn/hust/datn/casestudy/vulnshop/user/UserService.java#L66',
    );
  });

  /** `failureMessage` null trong một `testCase` KHÔNG null — nhánh lồng, dễ sót nhất. */
  it('testCase có mà failureMessage null thì vẫn render phần còn lại', () => {
    renderCard(
      itemVoiNull({
        testCase: {
          className: 'UserServiceTest',
          methodName: 'happyPath',
          status: 'PASSED',
          failureMessage: null,
        },
      }),
    );

    expect(screen.getByText(/UserServiceTest#happyPath/)).toBeInTheDocument();
    expect(screen.queryByText('Lỗi:')).not.toBeInTheDocument();
  });
});

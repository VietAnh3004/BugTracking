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

function renderCard(
  item: RelatedEvidence,
  neo: { anchorFilePath?: string | null; anchorStartLine?: number | null } = {},
) {
  return render(
    <MemoryRouter>
      <RelatedEvidenceCard item={item} snapshotId="snap-1" runId="run-1" {...neo} />
    </MemoryRouter>,
  );
}

const FILE_NEO = 'src/main/java/vn/hust/datn/casestudy/vulnshop/user/UserService.java';

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

  /**
   * Stack trace là một bằng chứng RIÊNG có Evidence ID riêng. Không có link thì câu lý do
   * "stack trace đi qua dòng 66" thành một khẳng định không kiểm chứng được — đúng thứ mà cả màn
   * hình này sinh ra để chống.
   */
  it('stackTraceShortId thành link mở đúng bằng chứng đó', () => {
    renderCard(
      itemVoiNull({
        testCase: {
          className: 'UserServiceTest',
          methodName: 'displayNameOf_khongTonTai_khongDuocNemNPE',
          status: 'FAILED',
          failureKind: 'ERROR',
          stackTraceShortId: 'EV-0453',
        },
      }),
    );

    expect(screen.getByRole('link', { name: 'EV-0453' })).toHaveAttribute(
      'href',
      '/snapshots/snap-1/runs/run-1/evidence/EV-0453',
    );
  });

  /**
   * `status` chuẩn hoá chỉ có PASSED/FAILED/SKIPPED nên nó GỘP hai kiểu hỏng khác hẳn nhau: `ERROR`
   * (ngoại lệ từ mã production — stack trace trỏ về đúng dòng cảnh báo) và `FAILURE` (assertion từ
   * chính lớp test). Chỉ nhóm ERROR mới làm được liên kết `STACK_FRAME`, nên gộp chúng là giấu đi
   * lý do một liên kết mạnh hơn liên kết khác.
   */
  it('phân biệt ERROR với FAILURE bên cạnh trạng thái đã gộp', () => {
    const { unmount } = renderCard(
      itemVoiNull({
        testCase: { className: 'T', methodName: 'm', status: 'FAILED', failureKind: 'ERROR' },
      }),
    );
    expect(screen.getByText('FAILED')).toBeInTheDocument();
    expect(screen.getByText('ERROR')).toBeInTheDocument();
    unmount();

    renderCard(
      itemVoiNull({
        testCase: { className: 'T', methodName: 'm', status: 'FAILED', failureKind: 'FAILURE' },
      }),
    );
    expect(screen.getByText('FAILURE')).toBeInTheDocument();
  });

  it('test không hỏng thì không có chip kiểu hỏng nào', () => {
    renderCard(
      itemVoiNull({
        testCase: { className: 'T', methodName: 'm', status: 'PASSED' },
      }),
    );

    expect(screen.getByText('PASSED')).toBeInTheDocument();
    expect(screen.queryByText('ERROR')).not.toBeInTheDocument();
    expect(screen.queryByText('FAILURE')).not.toBeInTheDocument();
  });

  it('hiện tên lớp ngoại lệ tách khỏi thông điệp', () => {
    renderCard(
      itemVoiNull({
        testCase: {
          className: 'T',
          methodName: 'm',
          status: 'FAILED',
          failureType: 'java.lang.NullPointerException',
          failureMessage: 'Cannot invoke User.email() because "user" is null',
        },
      }),
    );

    expect(screen.getByText('java.lang.NullPointerException')).toBeInTheDocument();
    expect(screen.getByText(/Cannot invoke User\.email/)).toBeInTheDocument();
  });

  /**
   * Bằng chứng độ phủ là cấp FILE nên không có `startLine` của riêng nó, trong khi câu lý do nói
   * "Dòng 66: COVERED". Link mở file ở đầu là bỏ rơi người dùng ở một file 70 dòng.
   */
  it('thẻ cấp file mượn dòng của cái neo khi CÙNG file', () => {
    renderCard(
      itemVoiNull({
        type: 'COVERAGE_INFORMATION',
        relation: 'COVERS',
        filePath: FILE_NEO,
        rationale: 'Dòng 66: COVERED (ci=5 mi=0 mb=0 cb=0)',
      }),
      { anchorFilePath: FILE_NEO, anchorStartLine: 66 },
    );

    expect(screen.getByRole('link', { name: /UserService\.java:66/ })).toHaveAttribute(
      'href',
      `/snapshots/snap-1/files/${FILE_NEO}#L66`,
    );
  });

  /**
   * Điều kiện "cùng file" là bắt buộc: thẻ test trỏ tới `UserServiceTest.java`, gắn dòng 66 của
   * `UserService.java` vào là nhảy tới một dòng hoàn toàn không liên quan — sai theo kiểu trông
   * vẫn hợp lý.
   */
  it('thẻ trỏ file KHÁC thì KHÔNG mượn dòng của neo', () => {
    const fileTest = 'src/test/java/vn/hust/datn/casestudy/vulnshop/user/UserServiceTest.java';
    renderCard(itemVoiNull({ type: 'FAILED_TEST', relation: 'SUPPORTS', filePath: fileTest }), {
      anchorFilePath: FILE_NEO,
      anchorStartLine: 66,
    });

    const link = screen.getByRole('link', { name: /UserServiceTest\.java/ });
    expect(link).toHaveAttribute('href', `/snapshots/snap-1/files/${fileTest}`);
    expect(link.getAttribute('href')).not.toContain('#L');
  });

  /** Thẻ có dòng riêng thì dùng dòng của chính nó, không bị neo đè lên. */
  it('thẻ có startLine riêng thì giữ nguyên dòng của nó', () => {
    renderCard(itemVoiNull({ filePath: FILE_NEO, startLine: 63 }), {
      anchorFilePath: FILE_NEO,
      anchorStartLine: 66,
    });

    expect(screen.getByRole('link', { name: /UserService\.java:63/ })).toHaveAttribute(
      'href',
      `/snapshots/snap-1/files/${FILE_NEO}#L63`,
    );
  });

  /**
   * `summary` viết cho LLM (một câu tự chứa), khối `dl` viết cho người (trường có nhãn). Với thẻ
   * có `testCase`, khối có cấu trúc phủ hết nội dung câu tóm tắt — hiện cả hai là nói mọi thứ hai
   * lần và làm thẻ dài gấp đôi.
   */
  it('thẻ có testCase thì KHÔNG lặp lại câu tóm tắt', () => {
    const tomTat =
      'Test hỏng (ERROR): UserServiceTest.displayNameOf_khongTonTai — java.lang.NullPointerException: user is null';
    renderCard(
      itemVoiNull({
        summary: tomTat,
        testCase: {
          className: 'UserServiceTest',
          methodName: 'displayNameOf_khongTonTai',
          status: 'FAILED',
          failureKind: 'ERROR',
          failureType: 'java.lang.NullPointerException',
          failureMessage: 'user is null',
        },
      }),
    );

    expect(screen.queryByText(tomTat)).not.toBeInTheDocument();
    // Nhưng không được mất thông tin: mọi mảnh của câu đó vẫn phải đọc được ở dạng có cấu trúc.
    expect(screen.getByText(/UserServiceTest#displayNameOf_khongTonTai/)).toBeInTheDocument();
    expect(screen.getByText('ERROR')).toBeInTheDocument();
    expect(screen.getByText('java.lang.NullPointerException')).toBeInTheDocument();
    expect(screen.getByText('user is null')).toBeInTheDocument();
  });

  /** Thẻ KHÔNG phải test (ngữ cảnh mã, độ phủ) không có khối có cấu trúc, nên vẫn cần câu tóm tắt. */
  it('thẻ không có testCase thì vẫn hiện câu tóm tắt', () => {
    renderCard(itemVoiNull({ summary: 'Độ phủ UserService.java: 13/14 dòng được phủ' }));

    expect(screen.getByText('Độ phủ UserService.java: 13/14 dòng được phủ')).toBeInTheDocument();
  });

  /**
   * Thời gian chạy CHỈ nằm trong câu tóm tắt của test xanh. Bỏ câu đó mà không đưa thời gian vào
   * khối có cấu trúc là làm MẤT dữ liệu, không phải bớt lặp.
   */
  it('test xanh vẫn đọc được thời gian chạy sau khi bỏ câu tóm tắt', () => {
    renderCard(
      itemVoiNull({
        summary: 'Test xanh: UserServiceTest.happyPath (12 ms)',
        testCase: {
          className: 'UserServiceTest',
          methodName: 'happyPath',
          status: 'PASSED',
          durationMs: 12,
        },
      }),
    );

    expect(
      screen.queryByText('Test xanh: UserServiceTest.happyPath (12 ms)'),
    ).not.toBeInTheDocument();
    expect(screen.getByText('12 ms')).toBeInTheDocument();
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

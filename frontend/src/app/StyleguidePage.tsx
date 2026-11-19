/**
 * /styleguide — toàn bộ design token và component dùng chung, ở MỌI trạng thái, trên một trang.
 *
 * Không phải tính năng cho người dùng: không có link nào trỏ tới đây trong ứng dụng. Nó tồn tại để
 * KIỂM BẰNG MẮT sau mỗi lần đổi token — đổi một màu thì mở trang này là thấy ngay chỗ nào vỡ, thay
 * vì đi qua mười màn hình cần dữ liệu thật mới hiện ra.
 *
 * Dữ liệu mẫu là dữ liệu THẬT của case study vulnshop (luật SpotBugs/Semgrep có trong
 * `docs/samples/vulnshop/`, đoạn mã từ `UserRepository.findByUsername`), không phải chữ giả: nhìn
 * bố cục với dữ liệu thật mới thấy đúng chỗ đường dẫn dài, câu mô tả dài làm vỡ hàng.
 *
 * Không cần đăng nhập — không gọi API nào.
 */

import { useMemo } from 'react';

import { VerdictBadge } from '@/features/chat/components/VerdictBadge';
import { DecisionBadge } from '@/features/review/components/DecisionBadge';
import { CodeLine } from '@/features/snapshots/components/CodeLine';
import { highlightLines } from '@/features/snapshots/lib/highlight';
import { SeverityBadge } from '@/features/warnings/components/SeverityBadge';
import { WarningTable } from '@/features/warnings/components/WarningTable';
import { IconAlert, IconChat, IconDownload, IconInbox, IconSearch } from '@/shared/ui/icons';
import { InfoTip } from '@/shared/ui/InfoTip';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { EmptyState, ErrorState, LoadingState, Skeleton } from '@/shared/ui/states';
import { buttonClass, CARD, INPUT } from '@/shared/ui/styles';
import { TextField } from '@/shared/ui/TextField';

import type { Warning } from '@/features/warnings/api/warningApi';
import type { ReactNode } from 'react';

// Tên token + vai trò. Giá trị đọc thẳng từ CSS lúc chạy (`var(--color-*)`), nên trang này không
// bao giờ lệch với `src/index.css`.
const SURFACES = [
  ['surface-muted', 'Nền trang'],
  ['surface', 'Thẻ, panel'],
  ['surface-sunken', 'Ô nhập, hover, dòng chọn'],
  ['surface-raised', 'Menu, tooltip'],
  ['border', 'Viền thường'],
  ['border-strong', 'Viền ô nhập, nút phụ'],
] as const;

const INKS = [
  ['ink', 'Chữ chính'],
  ['ink-muted', 'Chữ phụ'],
  ['ink-subtle', 'Nhãn, số dòng, placeholder'],
  ['brand', 'Link, chữ accent'],
  ['brand-solid', 'Nền nút chính (chữ trắng)'],
] as const;

const SEMANTIC = [
  ['danger', 'Lỗi, test hỏng, xoá'],
  ['warning', 'Cần chú ý'],
  ['success', 'Xong, test qua'],
  ['info', 'Thông tin'],
  ['sev-high', 'HIGH'],
  ['sev-medium', 'MEDIUM'],
  ['sev-low', 'LOW'],
  ['sev-info', 'INFO'],
] as const;

const TYPE_SCALE = [
  ['text-2xs', '11px', 'Tiêu đề cột bảng, nhãn rất nhỏ'],
  ['text-xs', '12px', 'Đường dẫn file, chú thích'],
  ['text-sm', '13px', 'Chữ thân mặc định'],
  ['text-base', '14px', 'Tiêu đề thẻ'],
  ['text-lg', '16px', '—'],
  ['text-xl', '20px', 'Tiêu đề trang'],
  ['text-2xl', '24px', '—'],
] as const;

// Cảnh báo THẬT của vulnshop — vị trí, luật, mức độ (priority 1/2/3 của SpotBugs = HIGH/MEDIUM/LOW)
// và thông điệp lấy nguyên từ docs/samples/vulnshop/spotbugs.xml. Hàng thứ tư dựng từ cảnh báo thật
// CRLF_INJECTION_LOGS ở AuditLogger.java:29, CỐ Ý bỏ `filePath` để hiện trạng thái "chưa đối chiếu
// được với snapshot" — trạng thái đó phải luôn hiện ra, không được giấu.
const VULNSHOP = 'src/main/java/vn/hust/datn/casestudy/vulnshop';
const SAMPLE_WARNINGS: Warning[] = [
  sample(
    'EV-01A7',
    'HIGH',
    'WEAK_MESSAGE_DIGEST_MD5',
    `${VULNSHOP}/auth/PasswordHasher.java`,
    40,
    'This API MD5 (MDX) is not a recommended cryptographic hash function',
  ),
  sample(
    'EV-02C3',
    'MEDIUM',
    'SQL_INJECTION_SPRING_JDBC',
    `${VULNSHOP}/user/UserRepository.java`,
    35,
    'This use of org/springframework/jdbc/core/JdbcTemplate.query(Ljava/lang/String;Lorg/springframework/jdbc/core/RowMapper;)Ljava/util/List; can be vulnerable to SQL injection',
  ),
  sample(
    'EV-0B19',
    'LOW',
    'CRLF_INJECTION_LOGS',
    `${VULNSHOP}/util/AuditLogger.java`,
    24,
    'This use of org/slf4j/Logger.info(Ljava/lang/String;)V might be used to include CRLF characters into log messages',
  ),
  sample(
    'EV-0B1A',
    'LOW',
    'CRLF_INJECTION_LOGS',
    undefined,
    undefined,
    'This use of org/slf4j/Logger.info(Ljava/lang/String;Ljava/lang/Object;)V might be used to include CRLF characters into log messages',
  ),
];

function sample(
  shortId: string,
  severity: Warning['severityNormalized'],
  rule: string,
  filePath: string | undefined,
  line: number | undefined,
  summary: string,
): Warning {
  return {
    evidenceId: shortId,
    shortId,
    analysisRunId: 'styleguide',
    filePath,
    startLine: line,
    endLine: line,
    summary,
    tool: 'spotbugs',
    ruleId: rule,
    ruleIdNormalized: rule,
    severityNormalized: severity,
    hasPrimaryLocation: filePath !== undefined,
  };
}

// Đoạn mã THẬT: case-study/vulnshop/.../user/UserRepository.java, dòng 33–41.
const SAMPLE_CODE = [
  '    public List<User> findByUsername(String username) {',
  '        String sql = "SELECT * FROM users WHERE username = \'" + username + "\'";',
  '        return jdbcTemplate.query(sql, ROW_MAPPER);',
  '    }',
  '',
  '    /** Truy vấn an toàn, dùng tham số ràng buộc — để đối chiếu với hàm bên trên. */',
  '    public List<User> findByRole(String role) {',
  '        return jdbcTemplate.query("SELECT * FROM users WHERE role = ?", ROW_MAPPER, role);',
  '    }',
];
const SAMPLE_FIRST_LINE = 33;
const SAMPLE_HIGHLIGHT = [34, 35];

export function StyleguidePage() {
  const segments = useMemo(() => highlightLines(SAMPLE_CODE, 'java'), []);

  return (
    <div className="flex flex-col gap-8">
      <header>
        <h1 className="text-ink m-0 text-xl font-semibold tracking-tight">Styleguide</h1>
        <p className="text-ink-muted mt-0.5 mb-0 text-sm">
          Design token và component dùng chung. Nguồn sự thật: <code>src/index.css</code> và{' '}
          <code>src/shared/ui/</code>.
        </p>
      </header>

      <Section title="Màu — nền và viền">
        <SwatchGrid items={SURFACES} />
      </Section>
      <Section title="Màu — chữ và accent">
        <SwatchGrid items={INKS} />
      </Section>
      <Section title="Màu — ngữ nghĩa (luôn kèm chữ/icon)">
        <SwatchGrid items={SEMANTIC} />
      </Section>

      <Section title="Cỡ chữ">
        <div className={`${CARD} divide-border divide-y`}>
          {TYPE_SCALE.map(([cls, px, use]) => (
            <div key={cls} className="flex items-baseline gap-4 px-4 py-2">
              <code className="text-ink-subtle w-24 shrink-0 text-xs">{cls}</code>
              <span className="text-ink-subtle w-12 shrink-0 text-xs">{px}</span>
              <span className={`text-ink ${cls}`}>
                Cảnh báo SQL_INJECTION tại UserRepository.java:35
              </span>
              <span className="text-ink-subtle ml-auto hidden text-xs md:inline">{use}</span>
            </div>
          ))}
          <div className="flex items-baseline gap-4 px-4 py-2">
            <code className="text-ink-subtle w-24 shrink-0 text-xs">font-mono</code>
            <span className="text-ink-subtle w-12 shrink-0 text-xs">13px</span>
            <span className="text-ink font-mono text-sm">
              {'jdbcTemplate.query(sql, ROW_MAPPER); // != -> =>'}
            </span>
          </div>
        </div>
      </Section>

      <Section title="Nút">
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" className={buttonClass('primary')}>
            <IconChat className="size-4" />
            Hỏi Agent về EV-02C3
          </button>
          <button type="button" className={buttonClass('secondary')}>
            <IconDownload className="size-4" />
            Tải báo cáo CSV
          </button>
          <button type="button" className={buttonClass('ghost')}>
            Huỷ
          </button>
          <button type="button" className={buttonClass('danger')}>
            Xoá dự án
          </button>
          <button type="button" className={buttonClass('dangerOutline')}>
            Dừng
          </button>
          <button type="button" className={buttonClass('primary')} disabled>
            Đang xử lý…
          </button>
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <button type="button" className={buttonClass('primary', 'sm')}>
            Nhỏ · chính
          </button>
          <button type="button" className={buttonClass('secondary', 'sm')}>
            Nhỏ · phụ
          </button>
          <button type="button" className={buttonClass('ghost', 'sm')}>
            Nhỏ · ghost
          </button>
        </div>
      </Section>

      <Section title="Ô nhập">
        <div className="grid max-w-2xl gap-3 sm:grid-cols-2">
          <TextField
            id="sg-path"
            label="Đường dẫn file"
            value=""
            onChange={() => undefined}
            placeholder="src/main/java/…"
            required={false}
          />
          <div className="flex flex-col gap-1.5">
            <label htmlFor="sg-select" className="text-ink text-sm font-medium">
              Mức độ
            </label>
            <select id="sg-select" className={INPUT} defaultValue="HIGH">
              <option value="HIGH">HIGH</option>
              <option value="MEDIUM">MEDIUM</option>
            </select>
          </div>
        </div>
      </Section>

      <Section title="Huy hiệu">
        <Row label="Mức độ">
          <SeverityBadge severity="HIGH" />
          <SeverityBadge severity="MEDIUM" />
          <SeverityBadge severity="LOW" />
          <SeverityBadge severity="INFO" />
        </Row>
        <Row label="Trạng thái">
          <StatusBadge tone="ok" label="Đã phân tích" />
          <StatusBadge tone="warning" label="Đang chạy" />
          <StatusBadge tone="danger" label="Lỗi" />
          <StatusBadge tone="neutral" label="Đang chờ" />
        </Row>
        <Row label="Đánh giá của người">
          <DecisionBadge decision="ACCEPT" />
          <DecisionBadge decision="REJECT" />
          <DecisionBadge decision="UNCERTAIN" />
          <DecisionBadge decision="INSUFFICIENT_EVIDENCE" />
          <DecisionBadge />
        </Row>
        <Row label="Kết luận của Agent">
          <VerdictBadge verdict="LIKELY_TRUE_POSITIVE" />
          <VerdictBadge verdict="LIKELY_FALSE_POSITIVE" />
          <VerdictBadge verdict="INSUFFICIENT_EVIDENCE" downgradedFrom="LIKELY_TRUE_POSITIVE" />
        </Row>
        <Row label="Chú thích">
          <span className="text-ink inline-flex items-center gap-1.5 text-sm font-semibold">
            Tỉ lệ nhận định không có căn cứ
            <InfoTip label="Giải thích: tỉ lệ nhận định không có căn cứ">
              Tỉ lệ nhận định không gắn được trích dẫn nào hợp lệ. Càng thấp càng tốt.
            </InfoTip>
          </span>
        </Row>
      </Section>

      <Section title="Bảng cảnh báo">
        <WarningTable
          warnings={SAMPLE_WARNINGS}
          snapshotId="styleguide"
          runId="styleguide"
          reviewByShortId={new Map([['EV-02C3', 'ACCEPT']])}
        />
      </Section>

      <Section title="Khung mã nguồn (dòng 34–35 được bằng chứng trỏ tới)">
        <div className={`${CARD} overflow-hidden`}>
          <div className="bg-code-bg overflow-x-auto font-mono text-sm leading-5">
            {SAMPLE_CODE.map((text, index) => {
              const lineNumber = SAMPLE_FIRST_LINE + index;
              return (
                <CodeLine
                  key={lineNumber}
                  lineNumber={lineNumber}
                  text={text}
                  segments={segments[index]}
                  highlighted={SAMPLE_HIGHLIGHT.includes(lineNumber)}
                />
              );
            })}
          </div>
        </div>
      </Section>

      <Section title="Trạng thái — đang tải (skeleton theo hình nội dung)">
        <div className="grid gap-4 lg:grid-cols-2">
          <LoadingState label="Đang tải cảnh báo…" shape="table" rows={3} />
          <LoadingState label="Đang tải nội dung file…" shape="code" rows={6} />
          <LoadingState label="Đang tải bằng chứng…" shape="detail" />
          <div className="flex flex-col gap-2">
            <LoadingState label="Đang tải danh sách dự án…" shape="lines" rows={3} />
            <Skeleton className="h-5 w-24" />
          </div>
        </div>
      </Section>

      <Section title="Trạng thái — trống">
        <div className="grid gap-4 lg:grid-cols-2">
          <div className={CARD}>
            <EmptyState
              icon={<IconSearch className="size-5" />}
              title="Không có cảnh báo nào khớp bộ lọc hiện tại."
              description="Nới bộ lọc — đường dẫn file chỉ cần gõ một phần, không phân biệt hoa thường."
              action={
                <button type="button" className={buttonClass('secondary', 'sm')}>
                  Xoá bộ lọc
                </button>
              }
            />
          </div>
          <div className={CARD}>
            <EmptyState
              icon={<IconInbox className="size-5" />}
              title="Chưa có snapshot nào."
              description="Tải lên một file zip mã nguồn ở khung bên phải để bắt đầu."
            />
          </div>
        </div>
      </Section>

      <Section title="Trạng thái — lỗi">
        <div className="grid gap-4 lg:grid-cols-2">
          <ErrorState
            title="Lần phân tích này đã hỏng"
            message="khong co target/classes sau khi build — ma nguon khong bien dich duoc (xem maven-test.log)"
            hint="Sửa nguyên nhân trong mã nguồn hoặc cấu hình rồi chạy phân tích lại từ trang snapshot."
            onRetry={() => undefined}
          />
          <ErrorState
            title="Không tải được danh sách cảnh báo."
            message="Failed to fetch"
            hint="Kiểm tra trạng thái backend ở chân trang rồi tải lại trang."
          />
        </div>
        <p className="text-warning mt-3 mb-0 inline-flex items-center gap-1.5 text-sm">
          <IconAlert className="size-4" />
          Cảnh báo nội dòng: màu luôn đi kèm icon.
        </p>
      </Section>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-ink-subtle text-2xs m-0 font-medium tracking-wider uppercase">{title}</h2>
      {children}
    </section>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-ink-subtle w-36 shrink-0 text-xs">{label}</span>
      {children}
    </div>
  );
}

function SwatchGrid({ items }: { items: readonly (readonly [string, string])[] }) {
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
      {items.map(([token, use]) => (
        <div key={token} className={`${CARD} flex items-center gap-3 p-2`}>
          <span
            aria-hidden="true"
            className="border-border-strong size-8 shrink-0 rounded-sm border"
            style={{ backgroundColor: `var(--color-${token})` }}
          />
          <span className="min-w-0">
            <code className="text-ink block truncate text-xs">{token}</code>
            <span className="text-ink-subtle block truncate text-xs">{use}</span>
          </span>
        </div>
      ))}
    </div>
  );
}

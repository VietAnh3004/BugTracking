import { describe, expect, it } from 'vitest';

import { displaySummary } from '@/features/warnings/lib/displaySummary';

describe('displaySummary', () => {
  it('bóc tiền tố công cụ/mức/luật và đuôi vị trí của SpotBugs', () => {
    expect(
      displaySummary(
        '[spotbugs/HIGH] WEAK_MESSAGE_DIGEST_MD5: MD2, MD4 and MD5 are weak hash functions — vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java:40',
      ),
    ).toBe('MD2, MD4 and MD5 are weak hash functions');
  });

  it('bóc được mã luật có gạch ngang của Semgrep và đường dẫn tuyệt đối', () => {
    expect(
      displaySummary(
        '[semgrep/MEDIUM] use-of-md5: Detected MD5 hash algorithm which is considered insecure — /work/vulnshop/src/main/java/App.java:40',
      ),
    ).toBe('Detected MD5 hash algorithm which is considered insecure');
  });

  it('giữ nguyên chuỗi không theo khuôn', () => {
    expect(displaySummary('Mat khau bam bang MD5')).toBe('Mat khau bam bang MD5');
  });

  /** Gạch ngang trong CÂU (không phải đuôi đường dẫn) là nội dung, không được cắt. */
  it('không cắt gạch ngang nằm giữa câu', () => {
    expect(displaySummary('[spotbugs/LOW] X: một — hai ba')).toBe('một — hai ba');
  });

  it('không trả chuỗi rỗng khi toàn bộ là tiền tố', () => {
    expect(displaySummary('[spotbugs/LOW] X:  ')).toBe('[spotbugs/LOW] X:  ');
  });
});

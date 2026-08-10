/**
 * Bộ test này canh ĐÚNG MỘT lỗi, nhưng là lỗi tệ nhất mà dashboard có thể mắc: hiện `0%` cho một
 * tỉ lệ chưa tính được.
 *
 * Backend đã cố ý trả `null` (không phải `0`) cho mọi tỉ lệ có mẫu số 0, và có test riêng canh
 * điều đó. Nếu frontend quy `null` về `0%` thì công sức ấy đổ sông: người chấm đọc "0% khớp với
 * Agent" trong khi sự thật là "chưa ai review cảnh báo nào".
 *
 * Chỗ trơn nhất: `openapi-typescript` khai các trường này là `?: number` (tức `number | undefined`)
 * nhưng runtime nhận `null` thật. Viết `value === undefined` thì TypeScript XANH, mà `null * 100`
 * bằng `0` nên màn hình hiện `0.0%`. Ca `null` dưới đây phải ép kiểu chính vì lý do đó — nó mô
 * phỏng dữ liệu thật, không phải một khả năng lý thuyết.
 */

import { describe, expect, it } from 'vitest';

import {
  CHUA_CO,
  formatCount,
  formatDecimal,
  formatRatio,
} from '@/features/review/lib/formatMetric';

describe('formatRatio', () => {
  it('null (thứ backend thật sự gửi) ra dấu gạch chứ KHÔNG phải 0%', () => {
    expect(formatRatio(null)).toBe(CHUA_CO);
  });

  it('undefined cũng ra dấu gạch', () => {
    expect(formatRatio(undefined)).toBe(CHUA_CO);
  });

  /** `0` là một phép đo hợp lệ và phải hiện thành số — đây là ranh giới với hai ca trên. */
  it('0 thật sự thì hiện 0.0%, không nhầm sang chưa có dữ liệu', () => {
    expect(formatRatio(0)).toBe('0.0%');
  });

  it('tỉ lệ 0..1 đổi thành phần trăm', () => {
    expect(formatRatio(0.125)).toBe('12.5%');
    expect(formatRatio(1)).toBe('100.0%');
  });
});

describe('formatDecimal', () => {
  it('null ra dấu gạch, kể cả khi có đơn vị', () => {
    expect(formatDecimal(null, ' ms')).toBe(CHUA_CO);
  });

  it('có giá trị thì kèm đơn vị', () => {
    expect(formatDecimal(1234.56, ' ms')).toBe('1234.6 ms');
  });
});

describe('formatCount', () => {
  /** Số đếm KHÔNG có khái niệm "chưa có": 0 nhận định không căn cứ là một tin tốt, không phải ẩn số. */
  it('0 hiện là 0 chứ không phải dấu gạch', () => {
    expect(formatCount(0)).toBe('0');
  });
});

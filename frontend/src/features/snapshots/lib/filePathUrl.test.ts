import { describe, expect, it } from 'vitest';

import {
  fromRouteParam,
  parseLineHash,
  toFilePathUrl,
  toLineHash,
} from '@/features/snapshots/lib/filePathUrl';

describe('toFilePathUrl', () => {
  it('giữ nguyên dấu / phân tách thư mục', () => {
    expect(toFilePathUrl('src/main/App.java')).toBe('src/main/App.java');
  });

  it('mã hoá khoảng trắng trong tên file', () => {
    expect(toFilePathUrl('tai lieu/ghi chu.txt')).toBe('tai%20lieu/ghi%20chu.txt');
  });

  it('mã hoá tên tiếng Việt có dấu', () => {
    expect(toFilePathUrl('tài liệu.md')).toBe('t%C3%A0i%20li%E1%BB%87u.md');
  });

  /**
   * `#` và `?` là hai ký tự cắt URL. Nếu dùng `encodeURI` (bỏ qua chúng), phần đuôi đường dẫn sẽ
   * bị trình duyệt hiểu thành fragment/query và mất hẳn.
   */
  it('mã hoá # và ? để không cắt mất phần đuôi đường dẫn', () => {
    expect(toFilePathUrl('bao cao #1?.md')).toBe('bao%20cao%20%231%3F.md');
  });

  it('mã hoá dấu % thành %25', () => {
    expect(toFilePathUrl('bao-cao-100%.txt')).toBe('bao-cao-100%25.txt');
  });
});

describe('fromRouteParam', () => {
  it('trả về đúng giá trị React Router đã giải mã, không giải mã thêm lần nữa', () => {
    // React Router v7 đưa vào giá trị ĐÃ giải mã. Gọi decodeURIComponent lần nữa trên chuỗi này
    // sẽ ném URIError vì '%.t' không phải chuỗi thoát hợp lệ.
    expect(fromRouteParam('bao-cao-100%.txt')).toBe('bao-cao-100%.txt');
  });

  it('đường dẫn nhiều cấp giữ nguyên', () => {
    expect(fromRouteParam('src/main/java/App.java')).toBe('src/main/java/App.java');
  });

  it('không có splat thì trả về chuỗi rỗng', () => {
    expect(fromRouteParam(undefined)).toBe('');
  });
});

describe('round-trip', () => {
  it('mã hoá rồi giải mã bằng cơ chế của trình duyệt cho lại đúng đường dẫn gốc', () => {
    const paths = [
      'src/main/App.java',
      'tài liệu/ghi chú.md',
      'bao cao #1?.md',
      'bao-cao-100%.txt',
      'a b/c d/e f.txt',
    ];

    for (const original of paths) {
      // Mô phỏng đúng việc React Router làm: giải mã theo từng đoạn.
      const decoded = toFilePathUrl(original).split('/').map(decodeURIComponent).join('/');
      expect(fromRouteParam(decoded)).toBe(original);
    }
  });
});

describe('parseLineHash', () => {
  it('đọc được số dòng từ #L42', () => {
    expect(parseLineHash('#L42')).toBe(42);
  });

  it('trả null khi không có hash', () => {
    expect(parseLineHash('')).toBeNull();
  });

  it('trả null với hash không phải dạng số dòng', () => {
    expect(parseLineHash('#section-2')).toBeNull();
    expect(parseLineHash('#L')).toBeNull();
    expect(parseLineHash('#L4x')).toBeNull();
  });

  it('trả null với #L0 vì dòng đánh số từ 1', () => {
    expect(parseLineHash('#L0')).toBeNull();
  });

  it('toLineHash và parseLineHash là cặp nghịch đảo', () => {
    expect(parseLineHash(toLineHash(123))).toBe(123);
  });
});

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
  /** Dạng cũ phải chạy y nguyên: mọi URL đã chia sẻ trước tuần 10-11 không được hỏng. */
  it('#L42 cho khoảng một dòng', () => {
    expect(parseLineHash('#L42')).toEqual({ fromLine: 42, toLine: 42 });
  });

  it('đọc được khoảng dòng từ #L40-L48', () => {
    expect(parseLineHash('#L40-L48')).toEqual({ fromLine: 40, toLine: 48 });
  });

  it('khoảng một dòng viết tường minh vẫn hợp lệ', () => {
    expect(parseLineHash('#L7-L7')).toEqual({ fromLine: 7, toLine: 7 });
  });

  it('trả null khi không có hash', () => {
    expect(parseLineHash('')).toBeNull();
  });

  it('trả null với hash không phải dạng số dòng', () => {
    expect(parseLineHash('#section-2')).toBeNull();
    expect(parseLineHash('#L')).toBeNull();
    expect(parseLineHash('#L4x')).toBeNull();
    expect(parseLineHash('#L10-')).toBeNull();
    expect(parseLineHash('#L10-20')).toBeNull();
  });

  it('trả null với #L0 vì dòng đánh số từ 1', () => {
    expect(parseLineHash('#L0')).toBeNull();
  });

  /** Khoảng ngược là lỗi của người viết URL, không phải một khoảng rỗng cần suy diễn. */
  it('trả null với khoảng ngược #L48-L40', () => {
    expect(parseLineHash('#L48-L40')).toBeNull();
  });
});

describe('toLineHash', () => {
  it('một dòng sinh #L42 chứ không phải #L42-L42', () => {
    expect(toLineHash(42)).toBe('#L42');
    expect(toLineHash(42, 42)).toBe('#L42');
  });

  it('khoảng nhiều dòng sinh #L40-L48', () => {
    expect(toLineHash(40, 48)).toBe('#L40-L48');
  });

  it('không có dòng thì không sinh hash', () => {
    expect(toLineHash(undefined)).toBe('');
    expect(toLineHash(null)).toBe('');
    expect(toLineHash(0)).toBe('');
  });

  /** endLine nhỏ hơn startLine là dữ liệu hỏng — không được sinh ra một URL mà parse lại thành null. */
  it('endLine nhỏ hơn startLine thì rơi về một dòng', () => {
    expect(toLineHash(40, 30)).toBe('#L40');
  });

  it('đi vòng qua parseLineHash cho lại đúng khoảng ban đầu', () => {
    for (const [from, to] of [
      [42, 42],
      [40, 48],
      [1, 2],
    ] as const) {
      expect(parseLineHash(toLineHash(from, to))).toEqual({ fromLine: from, toLine: to });
    }
  });
});

/**
 * NƠI DUY NHẤT xử lý việc chuyển đường dẫn file trong snapshot thành đoạn URL và ngược lại.
 *
 * Đóng gói vào một module vì mã hoá URL cho đường dẫn có hai cái bẫy ngược chiều nhau, và rải logic
 * này ra nhiều chỗ thì chắc chắn có chỗ làm sai:
 *
 * 1. **Phải mã hoá THEO TỪNG ĐOẠN, không mã hoá cả chuỗi.** `encodeURIComponent('a/b.java')` biến
 *    dấu `/` thành `%2F`, làm splat route `files/*` chỉ thấy MỘT đoạn thay vì cây thư mục. Ngược
 *    lại `encodeURI` lại bỏ qua `#` và `?`, nên tên file chứa `#` sẽ cắt mất phần đuôi đường dẫn.
 *    Cách đúng: tách theo `/`, mã hoá từng đoạn, rồi nối lại.
 *
 * 2. **KHÔNG được `decodeURIComponent` khi đọc ngược ra.** React Router v7 đã giải mã sẵn giá trị
 *    của `params['*']`. Giải mã lần nữa sẽ hỏng đúng những tên chứa ký tự `%` hợp lệ: một file tên
 *    `bao-cao-100%.txt` sau khi mã hoá là `bao-cao-100%25.txt`, React Router trả về đúng
 *    `bao-cao-100%.txt`, nhưng giải mã thêm lần nữa sẽ ném `URIError: URI malformed` và làm sập
 *    cả trang xem mã nguồn.
 */

/** Đường dẫn trong snapshot (`src/main/App.java`) → đoạn URL an toàn để ghép sau `files/`. */
export function toFilePathUrl(path: string): string {
  return path.split('/').map(encodeURIComponent).join('/');
}

/**
 * Giá trị `params['*']` của splat route → đường dẫn dùng để gọi API.
 *
 * Cố tình KHÔNG giải mã (xem cái bẫy số 2 ở trên): hàm này tồn tại để có một chỗ ghi rõ điều đó,
 * thay vì để mỗi component tự quyết định và có chỗ gọi thêm `decodeURIComponent`.
 */
export function fromRouteParam(splat: string | undefined): string {
  return splat ?? '';
}

/** Số dòng trong hash URL: `#L42` → 42. Trả `null` nếu không có hoặc không hợp lệ. */
export function parseLineHash(hash: string): number | null {
  const match = /^#L(\d+)$/.exec(hash);
  const digits = match?.[1];
  if (digits === undefined) {
    return null;
  }
  const line = Number.parseInt(digits, 10);
  // Dòng đánh số từ 1 (khớp `evidence_record.start_line`), nên `#L0` là vô nghĩa.
  return line >= 1 ? line : null;
}

export function toLineHash(line: number): string {
  return `#L${String(line)}`;
}

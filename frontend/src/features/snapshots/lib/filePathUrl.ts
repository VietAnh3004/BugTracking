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

/** Một khoảng dòng, 1-based và bao gồm cả hai đầu — cùng quy ước với `evidence_record`. */
export interface LineRange {
  fromLine: number;
  toLine: number;
}

/**
 * Khoảng dòng trong hash URL: `#L42` → {42, 42}, `#L40-L48` → {40, 48}.
 *
 * Trả `null` nếu không có hoặc không hợp lệ. Cú pháp khoảng lấy theo GitHub để URL vẫn đọc được
 * bằng mắt và người dùng đoán được cách sửa tay.
 *
 * Dạng `#L42` cũ vẫn chạy y nguyên: mọi URL đã chia sẻ trước tuần 10-11 không hỏng.
 */
export function parseLineHash(hash: string): LineRange | null {
  const match = /^#L(\d+)(?:-L(\d+))?$/.exec(hash);
  if (match === null) {
    return null;
  }
  const fromDigits = match[1];
  if (fromDigits === undefined) {
    return null;
  }
  const fromLine = Number.parseInt(fromDigits, 10);
  // Dòng đánh số từ 1 (khớp `evidence_record.start_line`), nên `#L0` là vô nghĩa.
  if (fromLine < 1) {
    return null;
  }
  const toDigits = match[2];
  if (toDigits === undefined) {
    return { fromLine, toLine: fromLine };
  }
  const toLine = Number.parseInt(toDigits, 10);
  // Khoảng ngược (`#L48-L40`) là lỗi của người viết URL, không phải một khoảng rỗng cần suy diễn.
  return toLine >= fromLine ? { fromLine, toLine } : null;
}

/**
 * Chiều ngược lại: dựng hash từ khoảng dòng.
 *
 * MỌI nơi dựng hash phải đi qua đây, không ghép chuỗi tay. Trước tuần 10-11 chỉ có `WarningTable`
 * tự ghép `#L${startLine}`; từ nay còn thẻ ngữ cảnh mã, frame stack trace, và citation của chatbot
 * ở tuần 12-13 — mỗi chỗ tự ghép là mỗi chỗ có thể sai một kiểu khác nhau.
 *
 * Một dòng thì sinh `#L42` chứ không phải `#L42-L42`: URL ngắn hơn và tương thích với mọi link đã
 * chia sẻ.
 */
export function toLineHash(startLine?: number | null, endLine?: number | null): string {
  if (startLine === undefined || startLine === null || startLine < 1) {
    return '';
  }
  if (endLine === undefined || endLine === null || endLine <= startLine) {
    return `#L${startLine}`;
  }
  return `#L${startLine}-L${endLine}`;
}

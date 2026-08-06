import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

import { type LineRange, parseLineHash } from '@/features/snapshots/lib/filePathUrl';

/**
 * Đọc khoảng dòng được trỏ bởi `#L42` hoặc `#L40-L48` trên URL, và cuộn tới đó.
 *
 * Phải tự cuộn bằng `useEffect` phụ thuộc `[hash, ready]` chứ không dựa vào hành vi mặc định của
 * trình duyệt: khi mở deep link ở tab mới, trình duyệt tìm phần tử `#L42` NGAY lúc tải trang, thời
 * điểm mà React chưa render dòng nào và nội dung file còn đang tải qua mạng. Nó không tìm thấy,
 * không cuộn, và cũng không thử lại — người dùng thấy file mở đúng nhưng ở đầu file.
 *
 * Cuộn tới dòng ĐẦU của khoảng, không phải giữa khoảng: với một method dài, thứ người đọc cần thấy
 * trước là chỗ bắt đầu.
 *
 * @param ready đặt `true` khi nội dung file đã render xong (dữ liệu về, không còn loading)
 */
export function useLineTarget(ready: boolean): LineRange | null {
  const { hash } = useLocation();
  const range = parseLineHash(hash);
  const fromLine = range?.fromLine ?? null;

  useEffect(() => {
    if (!ready || fromLine === null) {
      return;
    }
    const element = document.getElementById(`L${String(fromLine)}`);
    element?.scrollIntoView({ block: 'center' });
  }, [hash, ready, fromLine]);

  return range;
}

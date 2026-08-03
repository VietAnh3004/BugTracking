import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

import { parseLineHash } from '@/features/snapshots/lib/filePathUrl';

/**
 * Cuộn tới dòng được trỏ bởi `#L42` trên URL.
 *
 * Phải tự cuộn bằng `useEffect` phụ thuộc `[hash, ready]` chứ không dựa vào hành vi mặc định của
 * trình duyệt: khi mở deep link ở tab mới, trình duyệt tìm phần tử `#L42` NGAY lúc tải trang, thời
 * điểm mà React chưa render dòng nào và nội dung file còn đang tải qua mạng. Nó không tìm thấy,
 * không cuộn, và cũng không thử lại — người dùng thấy file mở đúng nhưng ở đầu file.
 *
 * @param ready đặt `true` khi nội dung file đã render xong (dữ liệu về, không còn loading)
 */
export function useLineTarget(ready: boolean): number | null {
  const { hash } = useLocation();
  const line = parseLineHash(hash);

  useEffect(() => {
    if (!ready || line === null) {
      return;
    }
    const element = document.getElementById(`L${String(line)}`);
    element?.scrollIntoView({ block: 'center' });
  }, [hash, ready, line]);

  return line;
}

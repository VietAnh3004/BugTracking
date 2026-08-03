/**
 * Chứng minh hạ tầng render hoạt động: React 19 + jsdom + Testing Library + matcher của jest-dom
 * (`toBeInTheDocument`), và `cleanup` giữa các test (hai test dưới cùng render một nhãn giống
 * nhau — thiếu cleanup thì test sau báo "found multiple elements").
 */

import { describe, expect, it } from 'vitest';

import { render, screen } from '@testing-library/react';

import { StatusBadge } from '@/shared/ui/StatusBadge';

describe('StatusBadge', () => {
  it('hiển thị nhãn được truyền vào', () => {
    render(<StatusBadge tone="ok" label="Backend: UP" />);

    expect(screen.getByText('Backend: UP')).toBeInTheDocument();
  });

  it('render lại được nhãn giống hệt ở test khác (chứng minh cleanup chạy)', () => {
    render(<StatusBadge tone="danger" label="Backend: UP" />);

    expect(screen.getByText('Backend: UP')).toBeInTheDocument();
  });

  /**
   * Canh đúng cạm bẫy đã ghi ở `src/index.css`: màu lấy từ BẢNG TRA tên class nguyên văn. Nếu ai
   * đó "gọn hoá" thành `bg-${tone}-100`, Tailwind không sinh ra class và lỗi HOÀN TOÀN IM LẶNG
   * trên giao diện — test này biến nó thành lỗi ồn ào.
   */
  it('gắn đúng class màu theo tone thay vì ghép chuỗi động', () => {
    render(<StatusBadge tone="danger" label="Mất kết nối" />);

    expect(screen.getByText('Mất kết nối')).toHaveClass('bg-red-100', 'text-red-800');
  });
});

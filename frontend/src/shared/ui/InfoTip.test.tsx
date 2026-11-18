import { describe, expect, it } from 'vitest';

import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { InfoTip } from '@/shared/ui/InfoTip';

/** Bong bóng nhìn thấy được (render qua portal, `aria-hidden`) — tìm theo chữ trong `body`. */
function bubble(text: string): HTMLElement | null {
  return (
    Array.from(document.body.querySelectorAll<HTMLElement>('div[aria-hidden="true"]')).find(
      (element) => element.textContent === text,
    ) ?? null
  );
}

describe('InfoTip', () => {
  it('chú thích không hiện sẵn; rê chuột vào "?" thì hiện, rời ra thì ẩn', async () => {
    render(<InfoTip label="Giải thích: Snapshot">Mỗi lần tải lên là một snapshot.</InfoTip>);
    const button = screen.getByRole('button', { name: 'Giải thích: Snapshot' });

    expect(bubble('Mỗi lần tải lên là một snapshot.')).toBeNull();
    await userEvent.hover(button);
    expect(bubble('Mỗi lần tải lên là một snapshot.')).not.toBeNull();
    await userEvent.unhover(button);
    expect(bubble('Mỗi lần tải lên là một snapshot.')).toBeNull();
  });

  it('trình đọc màn hình đọc được chú thích mà không cần mở bong bóng', () => {
    render(<InfoTip label="Giải thích: Thành viên">Thành viên xem được mã.</InfoTip>);

    expect(
      screen.getByRole('button', { name: 'Giải thích: Thành viên' }),
    ).toHaveAccessibleDescription('Thành viên xem được mã.');
  });

  it('chạm (bấm) thì ghim lại; chạm ra ngoài hoặc Escape thì đóng', async () => {
    render(
      <>
        <InfoTip label="Giải thích: Kappa">Mức đồng thuận.</InfoTip>
        <p>chỗ khác</p>
      </>,
    );
    const button = screen.getByRole('button', { name: 'Giải thích: Kappa' });

    fireEvent.click(button);
    expect(bubble('Mức đồng thuận.')).not.toBeNull();
    fireEvent.pointerDown(screen.getByText('chỗ khác'));
    expect(bubble('Mức đồng thuận.')).toBeNull();

    fireEvent.click(button);
    expect(bubble('Mức đồng thuận.')).not.toBeNull();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(bubble('Mức đồng thuận.')).toBeNull();
  });
});

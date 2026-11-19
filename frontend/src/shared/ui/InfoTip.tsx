/**
 * Nút "?" nhỏ cạnh một tiêu đề/nhãn; rê chuột, focus bằng bàn phím hoặc chạm vào thì hiện chú
 * thích. Thay cho các dòng chữ xám luôn hiện dưới tiêu đề — đọc một lần là đủ, để mãi thì rối mắt.
 *
 * Ba điều có chủ đích:
 * - Bong bóng render qua portal ra `document.body`, vị trí `fixed` tính từ nút: thẻ cha có
 *   `overflow-hidden` (như thẻ Snapshot) không cắt mất nó. Thiếu chỗ phía dưới thì lật lên trên,
 *   và luôn kẹp trong khung nhìn — trên điện thoại không tràn ra mép.
 * - Màn hình cảm ứng không có hover: chạm vào nút thì ghim bong bóng, chạm chỗ khác thì đóng.
 * - Trình đọc màn hình không phụ thuộc bong bóng: nội dung luôn có sẵn trong một span `sr-only`
 *   gắn vào nút qua `aria-describedby`; bong bóng nhìn thấy được thì `aria-hidden`.
 *
 * Nút đặt CẠNH tiêu đề, không đặt TRONG thẻ `<h2>`: đặt trong thì tên truy cập của tiêu đề dính
 * thêm chữ của nút.
 */

import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

import type { ReactNode } from 'react';

interface InfoTipProps {
  /** Tên truy cập của nút, vd "Giải thích: Snapshot" — chỉ trình đọc màn hình nghe thấy. */
  label: string;
  children: ReactNode;
}

const GAP = 6; // khoảng cách bong bóng ↔ nút
const MARGIN = 8; // khoảng cách tối thiểu tới mép khung nhìn

export function InfoTip({ label, children }: InfoTipProps) {
  const descriptionId = useId();
  const buttonRef = useRef<HTMLButtonElement>(null);
  const bubbleRef = useRef<HTMLDivElement>(null);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [pinned, setPinned] = useState(false);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);
  const open = hovered || focused || pinned;

  const place = useCallback(() => {
    const button = buttonRef.current;
    const bubble = bubbleRef.current;
    if (button === null || bubble === null) {
      return;
    }
    const anchor = button.getBoundingClientRect();
    const { width, height } = bubble.getBoundingClientRect();
    const below = anchor.bottom + GAP;
    const top =
      below + height + MARGIN > window.innerHeight && anchor.top - GAP - height >= MARGIN
        ? anchor.top - GAP - height
        : below;
    const centered = anchor.left + anchor.width / 2 - width / 2;
    const left = Math.min(Math.max(centered, MARGIN), window.innerWidth - width - MARGIN);
    setPosition({ top, left: Math.max(left, MARGIN) });
  }, []);

  // Đo trước khi vẽ: lần render đầu bong bóng ẩn (`invisible`), đo xong mới đặt vào chỗ.
  useLayoutEffect(() => {
    if (!open) {
      setPosition(null);
      return;
    }
    place();
  }, [open, place]);

  useEffect(() => {
    if (!open) {
      return;
    }
    function close(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setHovered(false);
        setPinned(false);
        setFocused(false);
      }
    }
    function unpinOutside(event: PointerEvent) {
      if (!buttonRef.current?.contains(event.target as Node)) {
        setPinned(false);
      }
    }
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    window.addEventListener('keydown', close);
    document.addEventListener('pointerdown', unpinOutside);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
      window.removeEventListener('keydown', close);
      document.removeEventListener('pointerdown', unpinOutside);
    };
  }, [open, place]);

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        aria-label={label}
        aria-describedby={descriptionId}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onClick={() => setPinned((value) => !value)}
        className={`inline-grid size-4 shrink-0 cursor-help place-items-center rounded-full border text-[10px] leading-none font-semibold transition-colors ${
          open
            ? 'border-brand text-brand bg-brand-subtle'
            : 'border-border-strong text-ink-subtle hover:border-brand hover:text-brand'
        }`}
      >
        <span aria-hidden="true">?</span>
      </button>
      <span id={descriptionId} className="sr-only">
        {children}
      </span>
      {open
        ? createPortal(
            <div
              ref={bubbleRef}
              aria-hidden="true"
              style={position === null ? { top: 0, left: 0 } : position}
              className={`bg-ink text-surface pointer-events-none fixed z-50 max-w-72 rounded-lg px-3 py-2 text-left text-xs leading-relaxed font-normal tracking-normal normal-case shadow-lg ${
                position === null ? 'invisible' : ''
              }`}
            >
              {children}
            </div>,
            document.body,
          )
        : null}
    </>
  );
}

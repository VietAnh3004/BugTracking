/**
 * Menu tài khoản trên thanh trên cùng: bấm vào avatar/tên thì mở hai lựa chọn "Cài đặt tài khoản"
 * và "Đăng xuất", thay cho link thẳng tới /account cộng một nút đăng xuất riêng đứng cạnh.
 *
 * Theo mẫu "menu button" của WAI-ARIA: nút mang `aria-haspopup="menu"` + `aria-expanded`; mở ra
 * thì focus vào mục đầu; ↑/↓ (và Home/End) chuyển mục, Esc đóng và trả focus về nút, Tab hoặc bấm
 * ra ngoài thì đóng. Phần tên + email ở đầu khung nằm NGOÀI `role="menu"` vì trong menu chỉ được
 * có mục chọn được.
 */

import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { useAuth } from '@/features/auth/hooks/useAuth';
import { Avatar } from '@/shared/ui/Avatar';
import { IconChevronDown, IconLogout, IconSettings } from '@/shared/ui/icons';
import { CARD } from '@/shared/ui/styles';

import type { CurrentUser } from '@/features/auth/api/authApi';
import type { KeyboardEvent } from 'react';

const ITEM =
  'text-ink hover:bg-surface-muted focus:bg-surface-muted flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm font-medium outline-none';

export function AccountMenu({ user }: { user: CurrentUser }) {
  const { signOut } = useAuth();
  const menuId = useId();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLUListElement>(null);

  function items(): HTMLElement[] {
    return Array.from(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
  }

  function close({ returnFocus }: { returnFocus: boolean }) {
    setOpen(false);
    if (returnFocus) {
      triggerRef.current?.focus();
    }
  }

  // Mở ra thì đưa focus vào mục đầu — người dùng bàn phím bấm ↓ là đi tiếp được ngay.
  useEffect(() => {
    if (open) {
      items()[0]?.focus();
    }
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    function closeOutside(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('pointerdown', closeOutside);
    return () => document.removeEventListener('pointerdown', closeOutside);
  }, [open]);

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      setOpen(true);
    }
  }

  function handleMenuKeyDown(event: KeyboardEvent<HTMLUListElement>) {
    const list = items();
    const current = list.indexOf(document.activeElement as HTMLElement);
    const move = (index: number) => list[(index + list.length) % list.length]?.focus();
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        move(current + 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        move(current - 1);
        break;
      case 'Home':
        event.preventDefault();
        move(0);
        break;
      case 'End':
        event.preventDefault();
        move(list.length - 1);
        break;
      case 'Escape':
        event.preventDefault();
        close({ returnFocus: true });
        break;
      case 'Tab':
        close({ returnFocus: false });
        break;
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={`Tài khoản: ${user.displayName}`}
        onClick={() => setOpen((value) => !value)}
        onKeyDown={handleTriggerKeyDown}
        className={`flex items-center gap-2 rounded-full py-0.5 pr-2 pl-0.5 md:pr-3 ${
          open ? 'bg-surface-sunken' : 'hover:bg-surface-sunken'
        }`}
      >
        <Avatar displayName={user.displayName} avatarUrl={user.avatarUrl} />
        <span className="text-ink hidden text-sm font-medium md:inline">{user.displayName}</span>
        <IconChevronDown
          className={`text-ink-muted size-4 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open ? (
        <div className={`${CARD} absolute top-full right-0 z-40 mt-2 w-64 p-1.5 shadow-lg`}>
          <div className="border-border mb-1 border-b px-3 pt-1.5 pb-2.5">
            <p className="text-ink m-0 truncate text-sm font-semibold">{user.displayName}</p>
            <p className="text-ink-muted m-0 truncate text-xs">{user.email}</p>
          </div>
          <ul
            ref={menuRef}
            id={menuId}
            role="menu"
            aria-label="Tài khoản"
            onKeyDown={handleMenuKeyDown}
            className="m-0 flex list-none flex-col gap-0.5 p-0"
          >
            <li role="none">
              <Link
                to="/account"
                role="menuitem"
                tabIndex={-1}
                onClick={() => setOpen(false)}
                className={ITEM}
              >
                <IconSettings className="text-ink-muted size-4" />
                Cài đặt tài khoản
              </Link>
            </li>
            <li role="none">
              <button
                type="button"
                role="menuitem"
                tabIndex={-1}
                onClick={() => {
                  setOpen(false);
                  void signOut();
                }}
                className={`${ITEM} hover:text-red-700 focus:text-red-700`}
              >
                <IconLogout className="size-4" />
                Đăng xuất
              </button>
            </li>
          </ul>
        </div>
      ) : null}
    </div>
  );
}

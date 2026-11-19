/**
 * Thanh chuyển giữa các mục của khu vực quản trị. Dùng `NavLink` với `end`: `/admin` là gốc nên nếu
 * thiếu `end` thì tab "Tài khoản" sáng lên cả ở `/admin/projects`.
 */

import { NavLink } from 'react-router-dom';

const TABS = [
  { to: '/admin', label: 'Tài khoản' },
  { to: '/admin/projects', label: 'Dự án' },
] as const;

export function AdminTabs() {
  return (
    <nav aria-label="Mục quản trị" className="border-border flex gap-1 border-b">
      {TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end
          className={({ isActive }) =>
            `-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
              isActive
                ? 'border-brand text-ink'
                : 'text-ink-muted hover:text-ink border-transparent'
            }`
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </nav>
  );
}

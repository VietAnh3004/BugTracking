/**
 * Chỉ khai context + kiểu, KHÔNG chứa JSX — tách khỏi `AuthProvider.tsx` để thoả oxlint
 * `react/only-export-components`: một file vừa export component vừa export giá trị thường sẽ phá
 * Fast Refresh của Vite (sửa context làm mất state của toàn bộ cây component).
 */

import { createContext } from 'react';

import type { AuthTokens, CurrentUser } from '@/features/auth/api/authApi';

/**
 * Union literal chứ không phải `enum` của TypeScript: `erasableSyntaxOnly` đang bật (xem
 * tsconfig), mà `enum` sinh ra mã lúc chạy nên không xoá được chỉ bằng cách bỏ chú thích kiểu.
 *
 * `bootstrapping` là trạng thái QUAN TRỌNG NHẤT ở đây: lúc mới tải trang, ứng dụng có refresh
 * token trong localStorage nhưng CHƯA biết nó còn hiệu lực hay không. Gộp trạng thái này vào
 * `anonymous` là nguyên nhân của lỗi "F5 ở deep link luôn nhảy về trang đăng nhập".
 */
export type AuthStatus = 'bootstrapping' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  signIn: (tokens: AuthTokens) => void;
  signOut: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

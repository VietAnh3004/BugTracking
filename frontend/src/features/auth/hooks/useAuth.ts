import { useContext } from 'react';

import { AuthContext, type AuthContextValue } from '@/features/auth/context/AuthContext';

/**
 * Ném lỗi thay vì trả về giá trị mặc định khi thiếu provider: một context auth "rỗng" âm thầm sẽ
 * làm mọi route guard tưởng người dùng chưa đăng nhập, và lỗi hiện ra dưới dạng "cứ bị đá về trang
 * đăng nhập" — rất khó lần ra nguyên nhân thật.
 */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context === null) {
    throw new Error('useAuth phải được dùng bên trong <AuthProvider>');
  }
  return context;
}

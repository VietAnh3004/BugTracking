/** Provider dùng chung cho toàn ứng dụng. */

import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

import { createQueryClient } from '@/shared/lib/queryClient';

// Tạo một lần ở tầm module cho ứng dụng thật (không đặt trong component - mỗi lần render lại sẽ
// dựng client mới và xoá sạch cache). Test tự dựng client riêng qua `createTestQueryClient`.
const queryClient = createQueryClient();

export function AppProviders({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

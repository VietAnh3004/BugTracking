/** Provider dùng chung cho toàn ứng dụng. */

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Không refetch khi chuyển tab: người dùng thường mở song song mã nguồn và tài liệu,
      // refetch liên tục vừa tốn request vừa làm giao diện nhấp nháy.
      refetchOnWindowFocus: false,
    },
  },
});

export function AppProviders({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

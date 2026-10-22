'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { useState } from 'react';

const ReactQueryProvider = ({ children }: { children: React.ReactNode }) => {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: 3,
            // 다른 탭으로 이동했다가 돌아왔을 때 자동으로 데이터를 다시 가져오는 기능이다.
            refetchOnWindowFocus: true,
            // 데이터가 stale 상태일 때 자동으로 refetch 실행
            refetchOnMount: true,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      {/* React Query Devtools는 개발 환경에서만 활성화되며,
          API 호출 상태와 캐싱된 데이터를 시각적으로 보여주어 디버깅에 매우 유용하다. */}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
};

export default ReactQueryProvider;

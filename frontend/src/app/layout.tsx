import type { Metadata } from 'next';
import localFont from 'next/font/local';
import NextTopLoader from 'nextjs-toploader';
import './globals.css';
import ReactQueryProvider from '@/shared/providers/ReactQueryProvider';
import { getUserProfileAction } from '@/actions/user';
import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import { userKeys } from '@/hooks/queries/keys';
import { getQueryClient } from '@/shared/lib/getQueryClient';

const pretendard = localFont({
  src: '../../public/fonts/PretendardVariable.woff2',
  display: 'swap',
  weight: '45 920',
  variable: '--font-pretendard',
});

export const metadata: Metadata = {
  title: {
    template: '%s | Contentria',
    default: 'Contentria',
  },
  description: 'Contentria is a blog platform',
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const queryClient = getQueryClient();
  await queryClient.prefetchQuery({
    queryKey: userKeys.profile(),
    queryFn: () => getUserProfileAction(false),
  });

  const dehydratedState = dehydrate(queryClient);

  return (
    <html lang="ko">
      <body className={pretendard.className}>
        {/* 페이지 전환 피드백: 이전 화면을 유지한 채 상단 진행 바만 표시 (GitHub 방식).
            느린 전환에서만 체감되고 빠른 전환에서는 거의 보이지 않는다. */}
        <NextTopLoader color="#4f46e5" height={3} showSpinner={false} shadow={false} />
        <ReactQueryProvider>
          <HydrationBoundary state={dehydratedState}>{children}</HydrationBoundary>
        </ReactQueryProvider>
      </body>
    </html>
  );
}

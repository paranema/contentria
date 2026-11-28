import type { Metadata } from 'next';
import localFont from 'next/font/local';
import NextTopLoader from 'nextjs-toploader';
import './globals.css';
import ReactQueryProvider from '@/shared/providers/ReactQueryProvider';
import { PublicConfigProvider } from '@/shared/providers/PublicConfigProvider';
import { readPublicConfig } from '@/shared/config/runtimeConfig';
import { getMyProfileOrNull } from '@/features/member/api/profile';
import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import { memberKeys } from '@/features/member/hooks/keys';
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
    queryKey: memberKeys.profile(),
    queryFn: () => getMyProfileOrNull(),
  });

  const dehydratedState = dehydrate(queryClient);
  const publicConfig = readPublicConfig();

  return (
    <html lang="ko">
      <body className={pretendard.className}>
        {/* 페이지 전환 시 이전 화면을 유지한 채 상단 진행 바만 표시합니다. */}
        <NextTopLoader color="#4f46e5" height={3} showSpinner={false} shadow={false} />
        <PublicConfigProvider config={publicConfig}>
          <ReactQueryProvider>
            <HydrationBoundary state={dehydratedState}>{children}</HydrationBoundary>
          </ReactQueryProvider>
        </PublicConfigProvider>
      </body>
    </html>
  );
}

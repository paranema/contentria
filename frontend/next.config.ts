import type { NextConfig } from 'next';

/**
 * Next.js 애플리케이션의 빌드 방식, 이미지 최적화, 보안 헤더, URL 매핑을 설정합니다.
 */
const nextConfig: NextConfig = {
  // 컨테이너 배포를 위해 실행에 필요한 최소 파일만 .next/standalone에 추출합니다.
  output: 'standalone',

  // 라우트 페이지로 인식할 파일 확장자를 지정합니다.
  pageExtensions: ['js', 'jsx', 'md', 'mdx', 'ts', 'tsx'],

  // next/image 최적화에 허용할 외부 도메인(Google 프로필, 미디어 스토리지)과 반응형 크기를 설정합니다.
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'lh3.googleusercontent.com',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: '**.contentria.com',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: '**.r2.dev',
        pathname: '/**',
      },
    ],
    imageSizes: [16, 32, 48, 96, 128, 256, 384],
    deviceSizes: [640, 750, 828, 1080, 1200, 1920, 2048, 3840],
  },

  // 모든 응답에 기본 보안 헤더를 적용합니다.
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          {
            key: 'Cross-Origin-Opener-Policy',
            value: 'same-origin-allow-popups',
          },
          {
            key: 'Cross-Origin-Embedder-Policy',
            value: 'credentialless',
          },
          {
            key: 'Content-Security-Policy',
            value: "frame-ancestors 'self' https://accounts.google.com",
          },
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin',
          },
        ],
      },
    ];
  },

  // 공개 블로그 주소(/@블로그명)를 내부 라우트(/user/블로그명)로 연결합니다.
  async rewrites() {
    return [
      {
        source: '/@:blogSlug',
        destination: '/user/:blogSlug',
      },
      {
        source: '/@:blogSlug/:postSlug',
        destination: '/user/:blogSlug/posts/:postSlug',
      },
    ];
  },
};

export default nextConfig;

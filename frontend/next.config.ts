// import createMDX from '@next/mdx';
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  output: 'standalone',
  pageExtensions: ['js', 'jsx', 'md', 'mdx', 'ts', 'tsx'],
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'lh3.googleusercontent.com',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: new URL(process.env.NEXT_PUBLIC_CDN_BASE_URL || 'https://images.contentria.com').hostname,
        pathname: '/media/**',
      },
    ],
    // 주로 사용할 이미지 크기 설정
    imageSizes: [16, 32, 48, 96, 128, 256, 384],
    deviceSizes: [640, 750, 828, 1080, 1200, 1920, 2048, 3840],
  },

  // 헤더 설정 추가
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
          // Prevent MIME-sniffing. If a response is served with an incorrect Content-Type,
          // the browser must not re-interpret it based on content (e.g. rendering a
          // disguised HTML file as a script). Complements server-side magic number
          // validation for uploaded images (see backend/docs/design-notes/media-upload-hardening.md).
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          // Strip path/query from cross-origin Referer to avoid leaking post slugs or
          // query parameters to third-party resources (images, analytics, etc.).
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin',
          },
        ],
      },
    ];
  },

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

// const withMDX = createMDX({});

// export default withMDX(nextConfig);
export default nextConfig;

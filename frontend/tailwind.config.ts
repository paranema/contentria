import type { Config } from 'tailwindcss';
import typography from '@tailwindcss/typography';
import type { PluginAPI } from 'tailwindcss/types/config';

/**
 * Tailwind CSS의 스캔 대상 경로, 커스텀 테마, 플러그인을 설정합니다.
 */
const config: Config = {
  // HTML 요소의 class 속성을 기준으로 다크 모드를 전환합니다.
  darkMode: ['class'],

  // 유틸리티 클래스를 스캔할 마크업 폴더 경로를 지정합니다. (docs/architecture/01-module-structure.md)
  content: [
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
    './src/features/**/*.{js,ts,jsx,tsx,mdx}',
    './src/shared/**/*.{js,ts,jsx,tsx,mdx}',
  ],

  // 기본 테마에 커스텀 폰트, 테두리 반경, 색상 토큰, 마크다운 타이포그래피를 확장합니다.
  theme: {
    extend: {
      fontFamily: {
        pretendard: ['var(--font-pretendard)'],
      },
      backgroundImage: {
        'gradient-radial': 'radial-gradient(var(--tw-gradient-stops))',
        'gradient-conic': 'conic-gradient(from 180deg at 50% 50%, var(--tw-gradient-stops))',
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
      },
      // globals.css의 CSS 변수와 연결되는 shadcn/ui 시맨틱 색상입니다.
      colors: {
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        chart: {
          '1': 'hsl(var(--chart-1))',
          '2': 'hsl(var(--chart-2))',
          '3': 'hsl(var(--chart-3))',
          '4': 'hsl(var(--chart-4))',
          '5': 'hsl(var(--chart-5))',
        },
      },
      // 마크다운 본문(prose)의 글꼴, 목록 마커 대비, 줄 간격(1.5)을 에디터와 뷰어에서 동일하게 맞춥니다.
      typography: (theme: PluginAPI['theme']) => ({
        DEFAULT: {
          css: {
            fontFamily: theme('fontFamily.pretendard'),
            lineHeight: '1.5',
            letterSpacing: '-0.01em',
            'h1, h2, h3, h4, h5, h6': {
              letterSpacing: '-0.02em',
            },
            '--tw-prose-bullets': theme('colors.gray.500'),
            '--tw-prose-counters': theme('colors.gray.600'),
          },
        },
        lg: {
          css: {
            lineHeight: '1.5',
          },
        },
      }),
    },
  },

  // 마크다운 본문 스타일링(prose)과 UI 애니메이션 유틸리티 플러그인을 등록합니다.
  plugins: [typography, require('tailwindcss-animate')],
};

export default config;

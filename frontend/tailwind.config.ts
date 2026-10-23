import type { Config } from 'tailwindcss';
import typography from '@tailwindcss/typography';
import type { PluginAPI } from 'tailwindcss/types/config';

const config: Config = {
  darkMode: ['class'],
  // Every folder with markup (docs/architecture.md). A class used only in a folder left out here
  // is never generated.
  content: [
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
    './src/features/**/*.{js,ts,jsx,tsx,mdx}',
    './src/shared/**/*.{js,ts,jsx,tsx,mdx}',
  ],
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
      // shadcn/ui
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
      typography: (theme: PluginAPI['theme']) => ({
        DEFAULT: {
          css: {
            fontFamily: theme('fontFamily.pretendard'),
            lineHeight: '1.5',
            letterSpacing: '-0.01em',
            'h1, h2, h3, h4, h5, h6': {
              letterSpacing: '-0.02em',
            },
            // 기본 마커가 너무 연하다 (bullets gray-300, counters gray-500).
            // 본문보다는 연하되 또렷이 보이는 단계로 — 에디터/리더 공통 적용.
            '--tw-prose-bullets': theme('colors.gray.500'),
            '--tw-prose-counters': theme('colors.gray.600'),
          },
        },
        // prose-lg sets its own lineHeight (~1.78) per element; override
        // so reader matches editor (1.5) on large screens too.
        lg: {
          css: {
            lineHeight: '1.5',
          },
        },
      }),
    },
  },
  plugins: [typography, require('tailwindcss-animate')],
};
export default config;

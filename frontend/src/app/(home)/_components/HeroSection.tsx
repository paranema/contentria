'use client';

import { PATHS } from '@/shared/constants/paths';
import { useMyProfile } from '@/features/member/hooks/useMyProfile';
import Link from 'next/link';
import { memo, useRef } from 'react';
import { TypeAnimation } from 'react-type-animation';

const MemoizedTypeAnimation = memo(TypeAnimation);

export default function HeroSection() {
  const { data: user } = useMyProfile();
  const h1Ref = useRef<HTMLHeadingElement>(null);

  const triggerWaveAnimation = (element: HTMLSpanElement | null) => {
    if (!element) {
      return;
    }

    const wordToAnimate = '이야기';
    const textBefore = '당신의 ';
    const textAfter = '를 세상에 펼쳐보세요.';
    const wordSpans = wordToAnimate
      .split('')
      .map((char, index) => `<span style="--i: ${index};" class="wave-char">${char}</span>`)
      .join('');
    element.innerHTML = `${textBefore}${wordSpans}${textAfter}`;

    const headingElement = h1Ref.current;
    if (headingElement) {
      headingElement.classList.remove('animate-wave');
      setTimeout(() => {
        headingElement.classList.add('animate-wave');
      }, 10);
    }
  };

  return (
    <section className="bg-gradient-to-br from-indigo-50 via-white to-white py-16 text-center md:py-32">
      <div className="container mx-auto max-w-4xl px-6">
        <h1
          ref={h1Ref}
          className="relative mb-6 break-keep text-3xl font-extrabold tracking-tight text-gray-900 sm:text-4xl md:text-5xl lg:text-6xl"
        >
          {/* 타이핑 중 줄바꿈으로 하단 콘텐츠가 밀리는 레이아웃 시프트를 막기 위해 완성 문장 높이를 미리 확보합니다. */}
          <span aria-hidden className="invisible">
            당신의 이야기를 세상에 펼쳐보세요.
          </span>
          <span className="absolute inset-0">
            <MemoizedTypeAnimation
              sequence={[
                1000,
                '당신의 이야기를 세상에 펼쳐보세요.',
                (el) => triggerWaveAnimation(el),
                5000,
              ]}
              wrapper="span"
              cursor={true}
              repeat={0}
              speed={20}
              className="inline-block"
            />
          </span>
        </h1>
        <p className="mb-10 text-lg text-gray-600 md:text-xl">
          당신의 이야기가 곧 당신의 브랜드가 됩니다.
        </p>

        <div className="mx-auto flex max-w-sm flex-col items-center justify-center gap-3 sm:max-w-none sm:flex-row sm:gap-4">
          {user ? (
            <>
              <Link
                href={PATHS.DASHBOARD}
                className="inline-flex items-center justify-center rounded-lg bg-indigo-600 px-8 py-3.5 text-base font-semibold text-white shadow-md transition duration-300 ease-in-out hover:bg-indigo-700 hover:shadow-lg"
              >
                대시보드로 이동
              </Link>
            </>
          ) : (
            <Link
              href={PATHS.LOGIN}
              className="inline-flex items-center justify-center rounded-lg bg-indigo-600 px-8 py-3.5 text-base font-semibold text-white shadow-md transition duration-300 ease-in-out hover:bg-indigo-700 hover:shadow-lg"
            >
              블로그 시작하기
            </Link>
          )}
        </div>
      </div>
    </section>
  );
}

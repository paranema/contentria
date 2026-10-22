'use client';

import { Heading } from '@/types/common';
import { useEffect, useState } from 'react';

interface TableOfContentsProps {
  headings: Heading[];
}

export default function TableOfContents({ headings }: TableOfContentsProps) {
  const [activeId, setActiveId] = useState<string>('');

  useEffect(() => {
    let ticking = false; // 💡 성능 최적화를 위한 플래그

    const handleScroll = () => {
      // 💡 requestAnimationFrame을 사용하여 브라우저 렌더링 주기에 맞춰 한 번만 실행되도록 제어
      if (!ticking) {
        window.requestAnimationFrame(() => {
          let currentId = '';
          for (const heading of headings) {
            const element = document.getElementById(heading.id);
            // 상단 기준 150px 위치를 지났는지 체크
            if (element && element.getBoundingClientRect().top < 150) {
              currentId = heading.id;
            } else {
              // 화면 아래에 있는 헤딩을 만나면 즉시 루프 종료 (성능 이점)
              break;
            }
          }
          setActiveId(currentId);
          ticking = false;
        });
        ticking = true;
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    handleScroll(); // 초기 로드 시 실행

    return () => window.removeEventListener('scroll', handleScroll);
  }, [headings]);

  return (
    <aside className="sticky top-24 max-h-[calc(100vh-6rem)] overflow-y-auto">
      <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-gray-700">
        On this page
      </h2>
      <ul className="space-y-2">
        <li>
          <a
            href="#"
            className={`block text-sm transition-colors hover:text-gray-900 ${
              // activeId가 비어있으면(스크롤이 맨 위면) 활성화
              activeId === '' ? 'font-semibold text-indigo-600' : 'text-gray-500'
            }`}
            onClick={(e) => {
              e.preventDefault();
              window.scrollTo({ top: 0, behavior: 'smooth' });
            }}
          >
            Introduction
          </a>
        </li>

        {headings.map((heading) => (
          <li key={heading.id} style={{ paddingLeft: `${(heading.level - 1) * 1}rem` }}>
            <a
              href={`#${heading.id}`}
              className={`block text-sm transition-colors hover:text-gray-900 ${
                activeId === heading.id ? 'font-semibold text-indigo-600' : 'text-gray-500'
              }`}
            >
              {heading.text}
            </a>
          </li>
        ))}
      </ul>
    </aside>
  );
}

'use client';

import { Heading } from '../../markdown/headings';
import { useEffect, useState } from 'react';

interface TableOfContentsProps {
  headings: Heading[];
}

export default function TableOfContents({ headings }: TableOfContentsProps) {
  const [activeId, setActiveId] = useState<string>('');

  useEffect(() => {
    let ticking = false;

    const handleScroll = () => {
      // 브라우저 렌더링 주기에 맞춰 스크롤 계산이 한 번만 실행되도록 제한합니다.
      if (!ticking) {
        window.requestAnimationFrame(() => {
          let currentId = '';
          for (const heading of headings) {
            const element = document.getElementById(heading.id);
            // 뷰포트 상단 150px 기준선을 지난 마지막 헤딩을 현재 위치로 간주합니다.
            if (element && element.getBoundingClientRect().top < 150) {
              currentId = heading.id;
            } else {
              // 기준선 아래에 있는 헤딩을 만나면 순회를 중단합니다.
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
    handleScroll();

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
              // 스크롤이 최상단에 있어 활성 헤딩이 없으면 도입부 항목을 강조합니다.
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

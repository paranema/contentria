import { Check } from 'lucide-react';

/** 쇼케이스에서 강조하는 실제 기능 목록 — 추상 문구 대신 구체 기능을 나열한다 */
const FEATURES = [
  '마크다운 단축키 중심의 빠른 글쓰기',
  '코드 블록 문법 하이라이팅 (GitHub Light 팔레트)',
  '표 · 체크리스트 · 인용 · 알림 상자',
  '글 상단 동영상 첨부 (HLS 스트리밍)',
  '방문자 · 조회수 통계 대시보드',
];

/**
 * "직관적인 글쓰기" 같은 주장 카드 대신, 에디터의 실제 모습을 CSS 목업으로 보여주는
 * 증거형 섹션. 모바일에서는 텍스트 → 목업 순서로 세로 스택된다.
 */
export default function EditorShowcaseSection() {
  return (
    <section className="bg-white py-16 md:py-24">
      <div className="container mx-auto max-w-6xl px-6">
        <div className="grid items-center gap-10 lg:grid-cols-2 lg:gap-16">
          {/* 왼쪽: 기능 설명 */}
          <div>
            <h2 className="mb-4 break-keep text-2xl font-bold text-gray-900 sm:text-3xl md:text-4xl">
              글쓰기에 필요한 모든 것, <span className="text-primary">이미 준비되어 있습니다</span>
            </h2>
            <p className="mb-8 break-keep text-base text-gray-600 md:text-lg">
              마크다운으로 쓰면 코드와 표가 그대로 살아나는 에디터. 발행 즉시 통계까지 따라옵니다.
            </p>
            <ul className="space-y-3">
              {FEATURES.map((feature) => (
                <li key={feature} className="flex items-start gap-2.5 text-gray-700">
                  <Check className="mt-0.5 h-5 w-5 shrink-0 text-primary" strokeWidth={2.5} />
                  <span className="break-keep text-sm sm:text-base">{feature}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* 오른쪽: 에디터 CSS 목업 (스크린샷 에셋 없이 제품의 실제 결과물을 묘사) */}
          <div className="overflow-hidden rounded-xl border border-gray-200 shadow-lg">
            {/* 윈도 크롬 */}
            <div className="flex items-center gap-1.5 border-b bg-gray-50 px-4 py-2.5">
              <span className="h-3 w-3 rounded-full bg-red-400" />
              <span className="h-3 w-3 rounded-full bg-amber-400" />
              <span className="h-3 w-3 rounded-full bg-green-400" />
              <span className="ml-3 text-xs text-gray-400">새 글 작성</span>
            </div>
            {/* 툴바 */}
            <div className="flex items-center gap-1 border-b bg-indigo-50/60 px-3 py-2 text-xs text-gray-500">
              <span className="rounded px-1.5 py-0.5 font-bold hover:bg-white">B</span>
              <span className="rounded px-1.5 py-0.5 italic">I</span>
              <span className="rounded px-1.5 py-0.5 underline">U</span>
              <span className="mx-1 h-3.5 w-px bg-gray-300" />
              <span className="rounded px-1.5 py-0.5">{'</>'}</span>
              <span className="rounded px-1.5 py-0.5">⊞</span>
              <span className="rounded px-1.5 py-0.5">🔗</span>
            </div>
            {/* 본문 */}
            <div className="space-y-3 bg-white px-5 py-5 text-left text-sm">
              <p className="text-lg font-bold text-gray-900">오늘 배운 것 정리</p>
              <p className="text-gray-700">
                Kotlin의 <strong>확장 함수</strong>는 기존 클래스에 메서드를{' '}
                <code className="rounded bg-gray-100 px-1 py-0.5 text-[0.8em] text-pink-600">
                  추가
                </code>
                하는 효과를 낸다.
              </p>
              {/* 코드 블록 — 실제 에디터의 GitHub Light 하이라이팅을 재현 */}
              <pre className="overflow-x-auto rounded-md bg-gray-50 p-3 font-mono text-xs leading-relaxed">
                <code>
                  <span className="text-[#cf222e]">fun</span>{' '}
                  <span className="text-[#8250df]">String.shout</span>() ={' '}
                  <span className="text-[#8250df]">uppercase</span>() +{' '}
                  <span className="text-[#0a3069]">&quot;!&quot;</span>
                  {'\n'}
                  <span className="text-[#6e7781]">{'// "contentria".shout()'}</span>
                </code>
              </pre>
              <div className="flex items-center gap-2 text-gray-700">
                <span className="flex h-4 w-4 items-center justify-center rounded border border-primary bg-primary text-[10px] text-white">
                  ✓
                </span>
                <span className="text-gray-500 line-through">에디터 만들기</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

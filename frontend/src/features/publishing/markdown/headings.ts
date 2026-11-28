import GithubSlugger from 'github-slugger';
import { toString } from 'mdast-util-to-string';
import remarkParse from 'remark-parse';
import { unified } from 'unified';
import { visit } from 'unist-util-visit';

/** 목차(TOC) 구성을 위해 본문에서 추출한 헤딩 항목입니다. */
export interface Heading {
  id: string;
  text: string;
  level: number;
}

/**
 * 마크다운 본문에서 1·2단계 헤딩을 추출하고 rehype-slug와 동일한 GitHub 슬러그 규칙으로 ID를 생성합니다.
 */
export function headingsOf(markdown: string): Heading[] {
  const headings: Heading[] = [];
  const slugger = new GithubSlugger();
  visit(unified().use(remarkParse).parse(markdown), 'heading', (node) => {
    if (node.depth <= 2) {
      const text = toString(node);
      headings.push({ id: slugger.slug(text), text, level: node.depth });
    }
  });
  return headings;
}

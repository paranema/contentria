import GithubSlugger from 'github-slugger';
import { toString } from 'mdast-util-to-string';
import remarkParse from 'remark-parse';
import { unified } from 'unified';
import { visit } from 'unist-util-visit';

/** A heading of a post's body, for its table of contents. */
export interface Heading {
  id: string;
  text: string;
  level: number;
}

/**
 * The body's first- and second-level headings, with the ids rehype-slug gives them when the body
 * is rendered (both follow GitHub's slugs), so the table of contents links land.
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

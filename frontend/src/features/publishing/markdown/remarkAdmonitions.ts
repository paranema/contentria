import type { Root } from 'mdast';
import { visit } from 'unist-util-visit';

const ADMONITION_TYPES = new Set(['note', 'tip', 'info', 'caution', 'warning', 'danger']);

export function remarkAdmonitions() {
  return (tree: Root) => {
    visit(tree, (node) => {
      if (
        node.type !== 'containerDirective' &&
        node.type !== 'leafDirective' &&
        node.type !== 'textDirective'
      ) {
        return;
      }

      const directive = node as { name: string; data?: Record<string, unknown> };
      if (!ADMONITION_TYPES.has(directive.name)) return;

      const data = directive.data || (directive.data = {});
      data.hName = 'div';
      data.hProperties = {
        className: `admonition admonition-${directive.name}`,
      };
    });
  };
}

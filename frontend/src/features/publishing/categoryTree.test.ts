import { describe, expect, it } from 'vitest';
import { ALL_POSTS_ID, buildCategoryTree } from './categoryTree';

describe('buildCategoryTree', () => {
  it('nests children under their parent, after a node for every post', () => {
    const tree = buildCategoryTree([
      { id: 'a', name: '기록', slug: '기록', parentId: null, displayOrder: 0 },
      { id: 'b', name: '독서', slug: '독서', parentId: 'a', displayOrder: 0 },
      { id: 'c', name: '일상', slug: '일상', parentId: null, displayOrder: 1 },
    ]);

    expect(tree.map((node) => node.id)).toEqual([ALL_POSTS_ID, 'a', 'c']);
    expect(tree[1].children.map((node) => node.name)).toEqual(['독서']);
    expect(tree[2].children).toEqual([]);
  });

  it('keeps a child whose parent is missing at the top rather than losing it', () => {
    const tree = buildCategoryTree([
      { id: 'b', name: '독서', slug: '독서', parentId: 'gone', displayOrder: 0 },
    ]);

    expect(tree.map((node) => node.id)).toEqual([ALL_POSTS_ID, 'b']);
  });
});

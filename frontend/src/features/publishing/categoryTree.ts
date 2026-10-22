import { CategoryView } from './types';

/** A category with its children, for the sidebar. The first node stands for every post. */
export interface CategoryTreeNode {
  id: string;
  name: string;
  slug: string;
  children: CategoryTreeNode[];
}

export const ALL_POSTS_ID = 'all';

/** The API lists top-level categories each followed by its children; this nests them. */
export function buildCategoryTree(categories: CategoryView[]): CategoryTreeNode[] {
  const nodes = new Map<string, CategoryTreeNode>();
  categories.forEach((category) =>
    nodes.set(category.id, {
      id: category.id,
      name: category.name,
      slug: category.slug,
      children: [],
    })
  );

  const tree: CategoryTreeNode[] = [];
  categories.forEach((category) => {
    const node = nodes.get(category.id)!;
    const parent = category.parentId ? nodes.get(category.parentId) : undefined;
    if (parent) parent.children.push(node);
    else tree.push(node);
  });

  return [{ id: ALL_POSTS_ID, name: '전체 보기', slug: '', children: [] }, ...tree];
}

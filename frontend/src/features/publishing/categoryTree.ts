import { CategoryView } from './types';

/** 사이드바에 표시할 카테고리 트리 노드입니다. 첫 번째 노드는 전체 글 보기를 나타냅니다. */
export interface CategoryTreeNode {
  id: string;
  name: string;
  slug: string;
  children: CategoryTreeNode[];
}

export const ALL_POSTS_ID = 'all';

/** 상위 카테고리와 하위 카테고리가 순서대로 나열된 API 응답을 중첩 트리로 변환합니다. */
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

import { CategoryResponse, CategoryTreeNode } from '@/types/api/category';

export function buildCategoryTree(
  categories: CategoryResponse[],
  _blogSlug: string
): CategoryTreeNode[] {
  const totalPostCount = categories.reduce((sum, category) => {
    if (!category.parentId) {
      return sum + category.postCount;
    }
    return sum;
  }, 0);

  const allPostsNode: CategoryTreeNode = {
    id: 'all',
    name: '전체 보기',
    slug: '',
    parentId: null,
    level: 0,
    postCount: totalPostCount,
    children: [],
  };

  const nodeMap = new Map<string, CategoryTreeNode>();

  categories.forEach((category) => {
    nodeMap.set(category.id, { ...category, children: [] });
  });

  const tree: CategoryTreeNode[] = [];

  categories.forEach((category) => {
    const node = nodeMap.get(category.id)!;

    if (category.parentId) {
      const parent = nodeMap.get(category.parentId);
      if (parent) {
        parent.children.push(node);
      } else {
        tree.push(node);
      }
    } else {
      tree.push(node);
    }
  });

  return [allPostsNode, ...tree];
}

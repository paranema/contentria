import { CategoryView } from '../../../types';

/** A row of the category editor. [level] 0 is top-level, 1 a child; new rows carry a temporary id. */
export interface EditableCategory {
  id: string;
  name: string;
  slug: string;
  parentId: string | null;
  level: number;
}

/** The API lists top-level categories each followed by its children, the editor's order already. */
export function toEditable(categories: CategoryView[]): EditableCategory[] {
  return categories.map(({ id, name, slug, parentId }) => ({
    id,
    name,
    slug,
    parentId,
    level: parentId ? 1 : 0,
  }));
}

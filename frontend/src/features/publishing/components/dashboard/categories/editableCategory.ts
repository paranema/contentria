import { CategoryView } from '../../../types';

/** 카테고리 편집기의 행 모델입니다. level 0은 상위, 1은 하위 카테고리이며 새 항목은 임시 ID를 가집니다. */
export interface EditableCategory {
  id: string;
  name: string;
  slug: string;
  parentId: string | null;
  level: number;
}

/** 상위와 하위 카테고리가 편집기 표시 순서대로 정렬된 API 응답을 편집용 목록으로 변환합니다. */
export function toEditable(categories: CategoryView[]): EditableCategory[] {
  return categories.map(({ id, name, slug, parentId }) => ({
    id,
    name,
    slug,
    parentId,
    level: parentId ? 1 : 0,
  }));
}

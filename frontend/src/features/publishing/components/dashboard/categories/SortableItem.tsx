'use client';

import { useSortable } from '@dnd-kit/sortable';
import { CategoryResponse } from '@/types/api/category';
import CategoryItemCard from './CategoryItemCard';

interface SortableItemProps {
  category: CategoryResponse;
  isFirst: boolean;
  prevItemLevel: number;

  isDraggingCurrent: boolean; // 내가 드래그 당하는 중인가?
  indicatorSide: 'top' | 'bottom' | null; // 선을 어디에 그릴 것인가?

  onRemove: (id: string) => void;
  onUpdateName: (id: string, name: string) => void;
  onIndent: (id: string) => void;
  onOutdent: (id: string) => void;
  onAddSubCategory: (parentId: string) => void;
}

export default function SortableItem({
  category,
  isFirst,
  prevItemLevel,
  isDraggingCurrent,
  indicatorSide,
  ...props
}: SortableItemProps) {
  const { attributes, listeners, setNodeRef, transition, isDragging } = useSortable({
    id: category.id,
    animateLayoutChanges: () => false,
  });

  const style = {
    transform: undefined,
    transition,
    opacity: isDragging || isDraggingCurrent ? 0.3 : 1,
    position: 'relative' as const,
    zIndex: isDraggingCurrent ? 0 : 1,
  };

  const canIndent =
    !isFirst && category.level === 0 && (prevItemLevel === 0 || prevItemLevel === 1);
  const canOutdent = category.level === 1;
  const showAddSubCategoryButton = category.level === 0;

  return (
    <div ref={setNodeRef} style={style} {...attributes}>
      {indicatorSide === 'top' && (
        <div className="absolute -top-[2px] left-0 right-0 z-50 h-[4px] rounded-full bg-indigo-500" />
      )}
      {indicatorSide === 'bottom' && (
        <div className="absolute -bottom-[2px] left-0 right-0 z-50 h-[4px] rounded-full bg-indigo-500" />
      )}
      <CategoryItemCard
        category={category}
        canIndent={canIndent}
        canOutdent={canOutdent}
        showAddSubCategoryButton={showAddSubCategoryButton}
        dragHandleProps={listeners}
        {...props}
      />
    </div>
  );
}

'use client';

import { Button } from '@/shared/ui/button';
import { EditableCategory, toEditable } from './editableCategory';
import {
  closestCorners,
  defaultDropAnimationSideEffects,
  DndContext,
  DragEndEvent,
  DragOverEvent,
  DragOverlay,
  DragStartEvent,
  DropAnimation,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { Plus, RotateCcw, Save } from 'lucide-react';
import { useMemo, useState } from 'react';
import SortableItem from './SortableItem';
import CategoryItemCard from './CategoryItemCard';
import { editCategoryTreeAction } from '../../../api/categories';
import { publishingErrorMessage } from '../../../errors';
import { CategoryTreeView } from '../../../types';
import { v7 as uuidv7 } from 'uuid';

interface CategoryManagerProps {
  blogSlug: string;
  initialTree: CategoryTreeView;
}

export default function CategoryManager({ blogSlug, initialTree }: CategoryManagerProps) {
  const [items, setItems] = useState<EditableCategory[]>(toEditable(initialTree.categories));
  // The tree's version when it was loaded; the API refuses a save over someone else's change.
  const [version, setVersion] = useState(initialTree.version);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [overId, setOverId] = useState<string | null>(null);

  // 특정 부모(Level 0)의 자식(Level 1) ID 목록 가져오기
  const getChildrenIds = (parentId: string, flattenedCategories: EditableCategory[]): string[] => {
    return flattenedCategories.filter((item) => item.parentId === parentId).map((item) => item.id);
  };

  const sortableItems = useMemo(() => {
    const activeItem = activeId ? items.find((item) => item.id === activeId) : null;

    if (activeItem && activeItem.level === 0) {
      const childrenIds = getChildrenIds(activeItem.id, items);
      return items.filter((item) => !childrenIds.includes(item.id)).map((item) => item.id);
    }

    return items.map((item) => item.id);
  }, [items, activeId]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }), // 5px 이상 움직여야 드래그 시작 (클릭 실수 방지)
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  // 인디케이터 위치 계산: 특정 아이템 위에 선을 그릴지, 아래에 그릴지 결정
  const getIndicatorSide = (itemId: string, itemIndex: number): 'top' | 'bottom' | null => {
    if (!activeId || !overId) {
      return null;
    }

    if (itemId !== overId) {
      return null;
    }

    const activeIndex = items.findIndex((item) => item.id === activeId);

    if (activeIndex < itemIndex) {
      return 'bottom';
    }
    return 'top';
  };

  const isChildOfActive = (itemId: string) => {
    if (!activeId) {
      return false;
    }
    const activeItem = items.find((item) => item.id === activeId);
    return (
      activeItem?.level === 0 && items.find((item) => item.id === itemId)?.parentId === activeId
    );
  };

  const handleDragStart = (event: DragStartEvent) => {
    setActiveId(event.active.id as string);
  };

  const handleDragOver = (event: DragOverEvent) => {
    setOverId((event.over?.id as string) || null);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    setActiveId(null);
    setOverId(null);

    if (!over || active.id === over.id) {
      return;
    }

    const oldIndex = items.findIndex((item) => item.id === active.id);
    const newIndex = items.findIndex((item) => item.id === over.id);
    const activeItem = items[oldIndex];

    const isMovingDown = oldIndex < newIndex;

    let newList = [...items];

    if (activeItem.level === 0) {
      const childrenIds = getChildrenIds(activeItem.id, items);
      const movingIds = [activeItem.id, ...childrenIds];

      const remainingList = newList.filter((item) => !movingIds.includes(item.id));
      const movingBlock = items.filter((item) => movingIds.includes(item.id));

      // 타겟 위치 찾기
      let targetIndex = remainingList.findIndex((item) => item.id === over.id);

      if (isMovingDown) {
        targetIndex++;
      }

      if (targetIndex < 0) {
        targetIndex = remainingList.length;
      }

      newList = [
        ...remainingList.slice(0, targetIndex),
        ...movingBlock,
        ...remainingList.slice(targetIndex),
      ];
    } else if (activeItem.level === 1) {
      const [movedItem] = newList.splice(oldIndex, 1);
      let targetIndex = newList.findIndex((item) => item.id === over.id);
      if (isMovingDown) {
        targetIndex++;
      }

      newList.splice(targetIndex, 0, movedItem);

      const insertedItem = newList[targetIndex];
      const prevItem = newList[targetIndex - 1];

      if (!prevItem) {
        insertedItem.level = 0;
        insertedItem.parentId = null;
      } else {
        if (prevItem.level === 0) {
          insertedItem.level = 1;
          insertedItem.parentId = prevItem.id;
        } else {
          insertedItem.level = 1;
          insertedItem.parentId = prevItem.parentId;
        }
      }
    }

    setItems(newList);
  };

  const handleDragCancel = () => {
    setActiveId(null);
    setOverId(null);
  };

  // 들여쓰기
  const handleIndent = (id: string) => {
    const index = items.findIndex((item) => item.id === id);
    if (index === 0) {
      return;
    }

    const currentItem = items[index];
    const prevItem = items[index - 1];

    if (currentItem.level !== 0) {
      alert('더 이상 들여쓰기를 할 수 없습니다.');
      return;
    }
    let newParentId: string | null = null;
    if (prevItem.level === 0) {
      newParentId = prevItem.id;
    } else if (prevItem.level === 1) {
      newParentId = prevItem.parentId;
    }

    if (newParentId) {
      setItems((prevItems) =>
        prevItems.map((item) => {
          if (item.id === id) {
            return { ...item, level: 1, parentId: newParentId };
          }
          return item;
        })
      );
    } else {
      alert('더 이상 들여쓰기를 할 수 없습니다.');
      return;
    }
  };

  // 내어쓰기
  const handleOutdent = (id: string) => {
    const currentItem = items.find((item) => item.id === id);

    if (currentItem?.level === 1) {
      setItems((prevItems) =>
        prevItems.map((item) => {
          if (item.id === id) {
            return { ...item, level: 0, parentId: null };
          }
          return item;
        })
      );
    }
  };

  const handleUpdateName = (id: string, newName: string) => {
    setItems(items.map((item) => (item.id === id ? { ...item, name: newName } : item)));
  };

  const handleRemove = (id: string) => {
    const targetItem = items.find((item) => item.id === id);
    if (!targetItem) {
      return;
    }

    if (window.confirm(`'${targetItem.name}' 카테고리를 삭제하시겠습니까?`)) {
      const children = getChildrenIds(id, items);

      if (children.length > 0) {
        alert(
          '하위 카테고리가 있는 카테고리는 삭제할 수 없습니다. 하위 카테고리를 먼저 삭제해주세요.'
        );
        return;
      }
      setItems(items.filter((item) => item.id !== id));
    }
  };

  const handleSave = async () => {
    setIsSaving(true);

    try {
      // The whole tree in the list's order. A new row's temporary id is its key; children name
      // their parent by its key.
      const result = await editCategoryTreeAction(blogSlug, {
        version,
        categories: items.map((item) => ({
          key: item.id,
          name: item.name,
          parentKey: item.level === 0 ? null : item.parentId,
        })),
      });
      if (!result.success) {
        alert(publishingErrorMessage(result.error));
        return;
      }
      // New rows now have real ids and slugs.
      setItems(toEditable(result.data.categories));
      setVersion(result.data.version);
      alert('카테고리가 성공적으로 저장되었습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleAddCategory = () => {
    const newCategory: EditableCategory = {
      id: uuidv7(),
      name: '새 카테고리',
      slug: '',
      parentId: null,
      level: 0,
    };
    setItems([...items, newCategory]);
  };

  const handleAddSubCategory = (parentId: string) => {
    const parentIndex = items.findIndex((item) => item.id === parentId);
    if (parentIndex === -1) {
      return;
    }

    let insertIndex = parentIndex + 1;
    while (insertIndex < items.length && items[insertIndex].parentId === parentId) {
      insertIndex++;
    }

    const newCategory: EditableCategory = {
      id: uuidv7(),
      name: '새 하위 카테고리',
      slug: '',
      parentId: parentId,
      level: 1,
    };

    const newItems = [...items];
    newItems.splice(insertIndex, 0, newCategory);

    setItems(newItems);
  };

  const renderDragOverlay = () => {
    if (!activeId) {
      return null;
    }

    const activeItem = items.find((item) => item.id === activeId);
    if (!activeItem) {
      return null;
    }

    const children =
      activeItem.level === 0 ? items.filter((item) => item.parentId === activeId) : [];

    return (
      <div className="flex flex-col gap-0">
        <CategoryItemCard category={activeItem} isOverlay />
        {children.map((child) => (
          <div key={child.id} style={{ marginLeft: '32px' }}>
            <CategoryItemCard category={child} isOverlay />
          </div>
        ))}
      </div>
    );
  };

  const dropAnimation: DropAnimation = {
    sideEffects: defaultDropAnimationSideEffects({
      styles: {
        active: { opacity: '0.4' },
      },
    }),
  };

  return (
    <div className="flex flex-col gap-6">
      {/* flex-wrap: 모바일에서 버튼 그룹이 줄바꿈되어 카드 밖으로 밀리지 않게 한다 */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b pb-4">
        <Button onClick={handleAddCategory}>
          <Plus size={16} />
          카테고리 추가
        </Button>
        <div className="flex gap-2">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => window.location.reload()}
            aria-label="변경사항 되돌리기"
          >
            <RotateCcw size={16} />
          </Button>
          <Button onClick={handleSave} disabled={isSaving}>
            <Save size={16} className="mr-2" />
            {isSaving ? '저장 중...' : '변경사항 저장'}
          </Button>
        </div>
      </div>

      <div className="min-h-[300px] rounded bg-gray-50 p-4">
        <DndContext
          sensors={sensors}
          collisionDetection={closestCorners}
          onDragStart={handleDragStart}
          onDragOver={handleDragOver}
          onDragEnd={handleDragEnd}
          onDragCancel={handleDragCancel}
        >
          <SortableContext items={sortableItems} strategy={verticalListSortingStrategy}>
            {items.length === 0 ? (
              <div className="flex h-32 items-center justify-center text-gray-400">
                카테고리가 없습니다.
              </div>
            ) : (
              items.map((item, index) => {
                const prevItemLevel = index > 0 ? items[index - 1].level : -1;
                const isDraggingCurrent = activeId === item.id || isChildOfActive(item.id);

                return (
                  <SortableItem
                    key={item.id}
                    category={item}
                    isFirst={index === 0}
                    prevItemLevel={prevItemLevel}
                    isDraggingCurrent={isDraggingCurrent}
                    indicatorSide={getIndicatorSide(item.id, index)}
                    onRemove={handleRemove}
                    onUpdateName={handleUpdateName}
                    onIndent={handleIndent}
                    onOutdent={handleOutdent}
                    onAddSubCategory={handleAddSubCategory}
                  />
                );
              })
            )}
          </SortableContext>

          <DragOverlay dropAnimation={dropAnimation}>{renderDragOverlay()}</DragOverlay>
        </DndContext>
      </div>

      <div className="text-xs text-gray-500">
        * 드래그하여 순서를 변경하거나, 화살표 버튼으로 계층을 설정할 수 있습니다. (최대 2단계)
      </div>
    </div>
  );
}

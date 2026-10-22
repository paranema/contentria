'use client';

import { EditableCategory } from './editableCategory';
import type { DraggableSyntheticListeners } from '@dnd-kit/core';
import {
  ChevronLeft,
  ChevronRight,
  CornerDownRight,
  Folder,
  GripVertical,
  MoreVertical,
  Plus,
  Trash2,
} from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu';

interface CategoryItemCardProps {
  category: EditableCategory;
  isOverlay?: boolean; // 드래그 중인 잔상(오버레이)인지 여부
  canIndent?: boolean;
  canOutdent?: boolean;
  showAddSubCategoryButton?: boolean;
  onRemove?: (id: string) => void;
  onUpdateName?: (id: string, name: string) => void;
  onIndent?: (id: string) => void;
  onOutdent?: (id: string) => void;
  onAddSubCategory?: (parentId: string) => void;
  dragHandleProps?: DraggableSyntheticListeners;
}

export default function CategoryItemCard({
  category,
  isOverlay,
  canIndent,
  canOutdent,
  showAddSubCategoryButton,
  onRemove,
  onUpdateName,
  onIndent,
  onOutdent,
  onAddSubCategory,
  dragHandleProps,
}: CategoryItemCardProps) {
  return (
    <div
      className={`group mb-2 flex items-center gap-2 rounded-md border bg-white p-3 ${isOverlay ? 'scale-105 shadow-xl ring-2 ring-indigo-500' : 'border-gray-200 hover:border-indigo-300'}`}
      style={{ marginLeft: isOverlay ? 0 : `${category.level * 32}px` }}
    >
      <div {...dragHandleProps} className="cursor-grab text-gray-400 hover:text-gray-700">
        <GripVertical size={20} />
      </div>

      {category.level > 0 ? (
        <CornerDownRight size={18} className="shrink-0 text-gray-400" />
      ) : (
        <Folder size={18} className="shrink-0 text-indigo-500" />
      )}

      {/* min-w-0: input의 내재 최소폭 때문에 좁은 화면에서 행이 카드 밖으로 넘치는 것을 방지 */}
      <input
        value={category.name}
        onChange={(e) => onUpdateName?.(category.id, e.target.value)}
        className="min-w-0 flex-1 rounded bg-transparent px-2 py-1 text-sm font-medium text-gray-700 outline-none focus:bg-gray-50 focus:ring-1 focus:ring-indigo-500"
        placeholder="카테고리명"
        readOnly={isOverlay}
      />

      <div className="flex shrink-0 items-center gap-1">
        {!isOverlay && (
          <>
            {/* 데스크톱: hover 시 노출되는 인라인 액션 */}
            <div className="hidden items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100 md:flex">
              {showAddSubCategoryButton && (
                <button
                  onClick={() => onAddSubCategory?.(category.id)}
                  className="p-1 text-gray-400 hover:text-indigo-600"
                >
                  <Plus size={18} />
                </button>
              )}

              <div className="mx-1 h-4 w-px bg-gray-200"></div>

              <button
                onClick={() => onOutdent?.(category.id)}
                disabled={!canOutdent}
                className="p-1 text-gray-400 hover:text-indigo-600 disabled:opacity-20"
              >
                <ChevronLeft size={18} />
              </button>
              <button
                onClick={() => onIndent?.(category.id)}
                disabled={!canIndent}
                className="p-1 text-gray-400 hover:text-indigo-600 disabled:opacity-20"
              >
                <ChevronRight size={18} />
              </button>

              <div className="mx-1 h-4 w-px bg-gray-200"></div>

              <button
                onClick={() => onRemove?.(category.id)}
                className="p-1 text-red-400 hover:text-red-600"
              >
                <Trash2 size={18} />
              </button>
            </div>

            {/* 모바일: hover가 없으므로 항상 보이는 ⋮ 메뉴로 제공 (인라인 버튼 4개는 폭도 초과) */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  className="p-1 text-gray-400 hover:text-gray-700 md:hidden"
                  aria-label="카테고리 작업 메뉴"
                >
                  <MoreVertical size={18} />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {showAddSubCategoryButton && (
                  <DropdownMenuItem onClick={() => onAddSubCategory?.(category.id)}>
                    <Plus size={16} className="mr-2" />
                    하위 카테고리 추가
                  </DropdownMenuItem>
                )}
                <DropdownMenuItem disabled={!canOutdent} onClick={() => onOutdent?.(category.id)}>
                  <ChevronLeft size={16} className="mr-2" />한 단계 내어쓰기
                </DropdownMenuItem>
                <DropdownMenuItem disabled={!canIndent} onClick={() => onIndent?.(category.id)}>
                  <ChevronRight size={16} className="mr-2" />한 단계 들여쓰기
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={() => onRemove?.(category.id)}
                  className="text-red-600 focus:text-red-600"
                >
                  <Trash2 size={16} className="mr-2" />
                  삭제
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </>
        )}
      </div>
    </div>
  );
}

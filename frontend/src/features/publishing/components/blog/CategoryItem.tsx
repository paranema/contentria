'use client';

import { useState } from 'react';
import Link from 'next/link';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { CategoryTreeNode } from '@/types/api/category';
import { useSearchParams } from 'next/navigation';

interface CategoryItemProps {
  category: CategoryTreeNode;
  blogSlug: string;
}

export default function CategoryItem({ category, blogSlug }: CategoryItemProps) {
  const searchParams = useSearchParams();
  const currentCategorySlug = searchParams.get('category');

  const [isExpanded, setIsExpanded] = useState(false);
  const hasChildren = category.children && category.children.length > 0;
  const isAllPosts = category.id === 'all';
  const isActive = isAllPosts ? !currentCategorySlug : currentCategorySlug === category.slug;
  const href = isAllPosts ? `/@${blogSlug}` : `/@${blogSlug}?category=${category.slug}`;

  return (
    <li className="my-1 text-sm">
      <div
        className={`group flex items-center justify-between rounded p-1 ${isActive ? 'bg-indigo-50 text-indigo-600' : 'hover:bg-gray-100'}`}
      >
        <Link href={href} className="flex flex-grow items-center">
          <span
            className={`font-medium ${isActive ? 'font-bold' : 'text-gray-700 group-hover:text-blue-600'}`}
          >
            {category.name}
          </span>

          {/* 게시글 수 뱃지 */}
          <span
            className={`ml-2 rounded-full px-2 py-0 text-xs font-semibold ${isActive ? 'bg-indigo-200 text-indigo-800' : 'bg-indigo-100 text-gray-600'}`}
          >
            {category.postCount}
          </span>
        </Link>

        {/* 확장/축소 버튼 */}
        {hasChildren && (
          <button
            onClick={(e) => {
              e.preventDefault();
              setIsExpanded(!isExpanded);
            }}
            className="ml-2 flex-shrink-0 p-1 text-gray-500 hover:text-gray-900 focus:outline-none"
            aria-label={`${category.name} 하위 카테고리 ${isExpanded ? '접기' : '펴기'}`}
          >
            {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
          </button>
        )}
      </div>

      {/* 하위 카테고리 (재귀적 렌더링) */}
      {hasChildren && isExpanded && (
        <ul className="ml-4 border-l border-gray-200 pl-3 pt-1">
          {category.children.map((childCategory) => (
            <CategoryItem
              key={childCategory.id ?? childCategory.slug}
              category={childCategory}
              blogSlug={blogSlug}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

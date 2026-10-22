'use client';

import { deletePostAction } from '@/actions/post';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu';
import { PostSummary } from '@/types/api/posts';
import { ColumnDef } from '@tanstack/react-table';
import { format, formatDistanceToNow, isToday } from 'date-fns';
import { ko } from 'date-fns/locale';
import { Edit, MoreHorizontal, Trash } from 'lucide-react';
import Link from 'next/link';

const formatDate = (dateString: string) => {
  const date = new Date(dateString);

  if (isToday(date)) {
    return formatDistanceToNow(date, { addSuffix: true, locale: ko });
  }

  return format(date, 'yyyy-MM-dd');
};

export const columns: ColumnDef<PostSummary>[] = [
  {
    accessorKey: 'title',
    header: '제목',
    cell: ({ row }) => {
      return (
        <div className="flex flex-col">
          <span className="max-w-[500px] truncate font-medium text-gray-900">
            {row.getValue('title')}
          </span>
        </div>
      );
    },
  },
  {
    accessorKey: 'status',
    header: '상태',
    cell: ({ row }) => {
      const status = row.getValue('status') as string;
      const isPublished = status === 'PUBLISHED';

      return (
        <Badge
          variant={isPublished ? 'default' : 'secondary'}
          className={
            isPublished
              ? 'border-green-200 bg-green-100 text-green-700 hover:bg-green-100'
              : 'border-gray-200 bg-gray-100 text-gray-700 hover:bg-gray-100'
          }
        >
          {isPublished ? '발행됨' : '임시저장'}
        </Badge>
      );
    },
  },
  {
    accessorKey: 'createdAt',
    header: '생성일',
    cell: ({ row }) => {
      const dateStr = row.getValue('createdAt') as string;
      return <div className="text-sm">{formatDate(dateStr)}</div>;
    },
  },
  {
    accessorKey: 'actions',
    header: '작업',
    cell: ({ row }) => {
      const post = row.original;

      return (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="h-8 w-8 p-0">
              <span className="sr-only">Open menu</span>
              <MoreHorizontal className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuLabel>Actions</DropdownMenuLabel>
            <DropdownMenuItem asChild>
              <Link href={`/dashboard/posts/${post.id}`}>
                <Edit className="mr-2 h-4 w-4" />
                수정
              </Link>
            </DropdownMenuItem>

            <DropdownMenuSeparator />

            <DropdownMenuItem
              className="cursor-pointer text-red-600 focus:bg-red-50 focus:text-red-600"
              onClick={async () => {
                const confirmed = confirm('정말 이 게시글을 삭제하시겠습니까?');
                if (confirmed) {
                  try {
                    await deletePostAction(post.id);
                  } catch (error) {
                    console.error('게시글 삭제 중 오류 발생:', error);
                    alert('게시글 삭제에 실패했습니다. 다시 시도해주세요.');
                  }
                }
              }}
            >
              <Trash className="mr-2 h-4 w-4" />
              삭제
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      );
    },
  },
];

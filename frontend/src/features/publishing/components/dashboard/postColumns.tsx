'use client';

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
import { ColumnDef } from '@tanstack/react-table';
import { format, formatDistanceToNow, isToday } from 'date-fns';
import { ko } from 'date-fns/locale';
import { Edit, Eye, EyeOff, MoreHorizontal, Send, Trash } from 'lucide-react';
import Link from 'next/link';
import { useTransition } from 'react';
import {
  deletePostAction,
  hidePostAction,
  publishPostAction,
  republishPostAction,
} from '../../api/posts';
import { publishingErrorMessage } from '../../errors';
import { PostListItem, PostStatus } from '../../types';
import { ActionResult } from '@/shared/api/actionResult';

const formatDate = (dateString: string) => {
  const date = new Date(dateString);
  if (isToday(date)) {
    return formatDistanceToNow(date, { addSuffix: true, locale: ko });
  }
  return format(date, 'yyyy-MM-dd');
};

const STATUS_BADGES: Record<PostStatus, { label: string; className: string }> = {
  PUBLISHED: {
    label: '발행됨',
    className: 'border-green-200 bg-green-100 text-green-700 hover:bg-green-100',
  },
  DRAFT: {
    label: '임시저장',
    className: 'border-gray-200 bg-gray-100 text-gray-700 hover:bg-gray-100',
  },
  PRIVATE: {
    label: '비공개',
    className: 'border-amber-200 bg-amber-100 text-amber-700 hover:bg-amber-100',
  },
};

/** What the owner can do with a post in each state (publishing 01-rules 흐름 4, 5). */
function PostActions({ post }: { post: PostListItem }) {
  const [isPending, startTransition] = useTransition();

  const run = (action: () => Promise<ActionResult<unknown>>, confirmation?: string) => {
    if (confirmation && !confirm(confirmation)) return;
    startTransition(async () => {
      const result = await action();
      if (!result.success) alert(publishingErrorMessage(result.error));
    });
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="h-8 w-8 p-0" disabled={isPending}>
          <span className="sr-only">Open menu</span>
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>Actions</DropdownMenuLabel>
        <DropdownMenuItem asChild>
          <Link href={`/dashboard/posts/${post.postId}`}>
            <Edit className="mr-2 h-4 w-4" />
            수정
          </Link>
        </DropdownMenuItem>
        {post.status === 'DRAFT' && (
          <DropdownMenuItem
            className="cursor-pointer"
            onClick={() => run(() => publishPostAction(post.postId))}
          >
            <Send className="mr-2 h-4 w-4" />
            발행
          </DropdownMenuItem>
        )}
        {post.status === 'PUBLISHED' && (
          <DropdownMenuItem
            className="cursor-pointer"
            onClick={() => run(() => hidePostAction(post.postId))}
          >
            <EyeOff className="mr-2 h-4 w-4" />
            비공개로 돌리기
          </DropdownMenuItem>
        )}
        {post.status === 'PRIVATE' && (
          <DropdownMenuItem
            className="cursor-pointer"
            onClick={() => run(() => republishPostAction(post.postId))}
          >
            <Eye className="mr-2 h-4 w-4" />
            다시 공개
          </DropdownMenuItem>
        )}

        <DropdownMenuSeparator />

        <DropdownMenuItem
          className="cursor-pointer text-red-600 focus:bg-red-50 focus:text-red-600"
          onClick={() =>
            run(() => deletePostAction(post.postId), '정말 이 게시글을 삭제하시겠습니까?')
          }
        >
          <Trash className="mr-2 h-4 w-4" />
          삭제
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export const postColumns: ColumnDef<PostListItem>[] = [
  {
    accessorKey: 'title',
    header: '제목',
    cell: ({ row }) => (
      <div className="flex flex-col">
        <span className="max-w-[500px] truncate font-medium text-gray-900">
          {row.getValue('title')}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'status',
    header: '상태',
    cell: ({ row }) => {
      const badge = STATUS_BADGES[row.getValue('status') as PostStatus];
      return (
        <Badge variant="secondary" className={badge.className}>
          {badge.label}
        </Badge>
      );
    },
  },
  {
    accessorKey: 'updatedAt',
    header: '수정일',
    cell: ({ row }) => (
      <div className="text-sm">{formatDate(row.getValue('updatedAt') as string)}</div>
    ),
  },
  {
    id: 'actions',
    header: '작업',
    cell: ({ row }) => <PostActions post={row.original} />,
  },
];

'use client';

import { formatPostDate } from '@/shared/lib/dateFormatUtil';
import { Button } from '@/shared/ui/button';
import { Textarea } from '@/shared/ui/textarea';
import Link from 'next/link';
import { useState, useTransition } from 'react';
import { deleteCommentAction, editCommentAction, writeCommentAction } from '../api/interactions';
import { interactionErrorMessage } from '../errors';
import { CommentAuthor, CommentPage, CommentView } from '../types';

/** 댓글 페이지 데이터와 작성자 프로필(member 컨텍스트)을 함께 묶은 구조입니다. */
export interface CommentsWithAuthors {
  page: CommentPage;
  authors: Record<string, CommentAuthor>;
}

interface CommentSectionProps {
  postId: string;
  initial: CommentsWithAuthors;
  /** 현재 로그인한 회원 정보이며, 비로그인 독자인 경우 null입니다. */
  viewer: { accountId: string; author: CommentAuthor } | null;
  /** 다음 댓글 페이지를 불러오는 서버 액션입니다. */
  loadMore: (postId: string, cursor: string) => Promise<CommentsWithAuthors>;
  loginRedirect: string;
}

/** 발행된 게시글 하단에 댓글과 1단계 답글 목록을 렌더링합니다. (interaction 01-rules 흐름 3) */
export default function CommentSection({
  postId,
  initial,
  viewer,
  loadMore,
  loginRedirect,
}: CommentSectionProps) {
  const [comments, setComments] = useState(initial.page.comments);
  const [cursor, setCursor] = useState(initial.page.nextCursor);
  const [authors, setAuthors] = useState(initial.authors);
  const [isPending, startTransition] = useTransition();

  const withViewer = (author: Record<string, CommentAuthor>) =>
    viewer ? { ...author, [viewer.accountId]: viewer.author } : author;

  const run = (work: () => Promise<void>) => startTransition(work);

  const write = (body: string, parentId: string | null, done: () => void) =>
    run(async () => {
      const result = await writeCommentAction(postId, body, parentId);
      if (!result.success) return alert(interactionErrorMessage(result.error));
      setAuthors(withViewer);
      setComments((list) =>
        parentId
          ? list.map((c) =>
              c.commentId === parentId ? { ...c, replies: [...c.replies, result.data] } : c
            )
          : [...list, result.data]
      );
      done();
    });

  const edit = (commentId: string, body: string, done: () => void) =>
    run(async () => {
      const result = await editCommentAction(commentId, body);
      if (!result.success) return alert(interactionErrorMessage(result.error));
      const replace = (c: CommentView): CommentView =>
        c.commentId === commentId
          ? { ...c, body: result.data.body, edited: true }
          : { ...c, replies: c.replies.map(replace) };
      setComments((list) => list.map(replace));
      done();
    });

  /** 답글이 달린 댓글은 삭제 시 자리를 유지하며, 마지막 답글까지 삭제되면 함께 제거합니다. (01-rules 흐름 3) */
  const remove = (comment: CommentView) =>
    run(async () => {
      if (!confirm('댓글을 지울까요?')) return;
      const result = await deleteCommentAction(comment.commentId);
      if (!result.success) return alert(interactionErrorMessage(result.error));
      setComments((list) =>
        list.flatMap((c) => {
          if (c.commentId === comment.commentId) {
            return c.replies.length > 0
              ? [{ ...c, body: '', removed: true, commenterId: null }]
              : [];
          }
          const replies = c.replies.filter((r) => r.commentId !== comment.commentId);
          return c.removed && replies.length === 0 ? [] : [{ ...c, replies }];
        })
      );
    });

  const more = () =>
    run(async () => {
      if (!cursor) return;
      const next = await loadMore(postId, cursor);
      setAuthors((current) => ({ ...current, ...next.authors }));
      setComments((list) => [...list, ...next.page.comments]);
      setCursor(next.page.nextCursor);
    });

  const count = comments.reduce((sum, c) => sum + (c.removed ? 0 : 1) + c.replies.length, 0);

  return (
    <section className="mt-12 border-t pt-8" aria-label="댓글">
      <h2 className="mb-6 text-lg font-semibold">댓글 {count > 0 ? count : ''}</h2>

      <ul className="space-y-6">
        {comments.map((comment) => (
          <li key={comment.commentId}>
            <CommentItem
              comment={comment}
              author={comment.commenterId ? authors[comment.commenterId] : undefined}
              mine={!!viewer && comment.commenterId === viewer.accountId}
              disabled={isPending}
              onEdit={edit}
              onRemove={remove}
            />
            {comment.replies.length > 0 && (
              <ul className="ml-6 mt-4 space-y-4 border-l pl-4">
                {comment.replies.map((reply) => (
                  <li key={reply.commentId}>
                    <CommentItem
                      comment={reply}
                      author={reply.commenterId ? authors[reply.commenterId] : undefined}
                      mine={!!viewer && reply.commenterId === viewer.accountId}
                      disabled={isPending}
                      onEdit={edit}
                      onRemove={remove}
                    />
                  </li>
                ))}
              </ul>
            )}
            {viewer && !comment.removed && (
              <ReplyForm
                disabled={isPending}
                onSubmit={(body, done) => write(body, comment.commentId, done)}
              />
            )}
          </li>
        ))}
      </ul>

      {cursor && (
        <Button variant="ghost" className="mt-6 w-full" disabled={isPending} onClick={more}>
          댓글 더 보기
        </Button>
      )}

      <div className="mt-8">
        {viewer ? (
          <CommentForm
            placeholder="댓글을 남겨주세요"
            disabled={isPending}
            onSubmit={(body, done) => write(body, null, done)}
          />
        ) : (
          <p className="text-sm text-muted-foreground">
            댓글을 쓰려면{' '}
            <Link
              className="font-medium text-indigo-600 hover:underline"
              href={`/login?redirect=${encodeURIComponent(loginRedirect)}`}
            >
              로그인
            </Link>
            하세요.
          </p>
        )}
      </div>
    </section>
  );
}

function CommentItem({
  comment,
  author,
  mine,
  disabled,
  onEdit,
  onRemove,
}: {
  comment: CommentView;
  author: CommentAuthor | undefined;
  mine: boolean;
  disabled: boolean;
  onEdit: (commentId: string, body: string, done: () => void) => void;
  onRemove: (comment: CommentView) => void;
}) {
  const [editing, setEditing] = useState(false);

  if (comment.removed) {
    return <p className="text-sm italic text-gray-400">삭제된 댓글입니다.</p>;
  }

  return (
    <div>
      <div className="flex items-center gap-2 text-sm">
        <span className="font-semibold text-gray-900">
          {author?.displayName ?? '알 수 없는 회원'}
        </span>
        <span className="text-gray-400">{formatPostDate(comment.createdAt)}</span>
        {comment.edited && <span className="text-xs text-gray-400">(수정됨)</span>}
        {mine && !editing && (
          <span className="ml-auto flex gap-2 text-xs">
            <button
              className="text-gray-500 hover:text-gray-900"
              disabled={disabled}
              onClick={() => setEditing(true)}
            >
              수정
            </button>
            <button
              className="text-red-500 hover:text-red-700"
              disabled={disabled}
              onClick={() => onRemove(comment)}
            >
              삭제
            </button>
          </span>
        )}
      </div>
      {editing ? (
        <CommentForm
          initial={comment.body}
          submitLabel="수정"
          disabled={disabled}
          onCancel={() => setEditing(false)}
          onSubmit={(body, done) =>
            onEdit(comment.commentId, body, () => (done(), setEditing(false)))
          }
        />
      ) : (
        <p className="mt-1 whitespace-pre-wrap text-gray-800">{comment.body}</p>
      )}
    </div>
  );
}

function ReplyForm({
  disabled,
  onSubmit,
}: {
  disabled: boolean;
  onSubmit: (body: string, done: () => void) => void;
}) {
  const [open, setOpen] = useState(false);
  if (!open) {
    return (
      <button
        className="ml-6 mt-2 text-xs text-gray-500 hover:text-gray-900"
        onClick={() => setOpen(true)}
      >
        답글 달기
      </button>
    );
  }
  return (
    <div className="ml-6 mt-3">
      <CommentForm
        placeholder="답글을 남겨주세요"
        disabled={disabled}
        onCancel={() => setOpen(false)}
        onSubmit={(body, done) => onSubmit(body, () => (done(), setOpen(false)))}
      />
    </div>
  );
}

/** 앞뒤 공백 제거 후 1~1000자 범위의 댓글·답글 입력 폼을 렌더링합니다. (01-rules 댓글 본문) */
function CommentForm({
  initial = '',
  placeholder,
  submitLabel = '등록',
  disabled,
  onSubmit,
  onCancel,
}: {
  initial?: string;
  placeholder?: string;
  submitLabel?: string;
  disabled: boolean;
  onSubmit: (body: string, done: () => void) => void;
  onCancel?: () => void;
}) {
  const [body, setBody] = useState(initial);
  const trimmed = body.trim();
  return (
    <form
      className="space-y-2"
      onSubmit={(event) => {
        event.preventDefault();
        if (trimmed) onSubmit(trimmed, () => setBody(''));
      }}
    >
      <Textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder={placeholder}
        maxLength={1000}
        rows={3}
      />
      <div className="flex justify-end gap-2">
        {onCancel && (
          <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
            취소
          </Button>
        )}
        <Button type="submit" size="sm" disabled={disabled || trimmed.length === 0}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

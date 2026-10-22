import { SerializedError } from '@/shared/api/actionResult';

const MESSAGES: Record<string, string> = {
  OWN_BLOG: '내 블로그는 구독할 수 없습니다.',
  OWN_POST: '내 글에는 좋아요를 누를 수 없습니다.',
  INVALID_COMMENT: '댓글은 1~1000자로 써주세요.',
  REPLY_TO_REPLY: '답글에는 답글을 달 수 없습니다.',
  INVALID_PARENT: '답글을 달 수 없는 댓글입니다.',
  COMMENT_REMOVED: '삭제된 댓글입니다.',
  COMMENT_NOT_FOUND: '댓글을 찾을 수 없습니다.',
  NOT_COMMENT_AUTHOR: '내가 쓴 댓글만 고치거나 지울 수 있습니다.',
  POST_NOT_FOUND: '글을 찾을 수 없습니다.',
  BLOG_NOT_FOUND: '블로그를 찾을 수 없습니다.',
  ACCOUNT_NOT_ACTIVE: '지금은 이 작업을 할 수 없는 계정입니다.',
};

export function interactionErrorMessage(error: Pick<SerializedError, 'code'>): string {
  return MESSAGES[error.code] ?? '요청을 처리하지 못했습니다. 다시 시도해주세요.';
}

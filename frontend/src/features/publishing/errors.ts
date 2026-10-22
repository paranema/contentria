import { SerializedError } from '@/shared/api/actionResult';

const MESSAGES: Record<string, string> = {
  BLOG_LIMIT_REACHED: '블로그는 회원당 3개까지 만들 수 있습니다.',
  BLOG_SLUG_TAKEN: '이미 쓰이는 블로그 주소입니다.',
  BLOG_SLUG_RESERVED: '쓸 수 없는 블로그 주소입니다.',
  INVALID_BLOG_SLUG: '블로그 주소는 영문 소문자, 숫자, 하이픈으로 3~30자입니다.',
  INVALID_BLOG_SETTINGS: '블로그 제목은 1~50자, 설명은 300자 이하입니다.',
  NOT_BLOG_OWNER: '내 블로그가 아닙니다.',
  BLOG_NOT_FOUND: '블로그를 찾을 수 없습니다.',
  CATEGORY_TREE_CHANGED: '다른 곳에서 카테고리가 바뀌었습니다. 새로 고친 뒤 다시 편집해 주세요.',
  CATEGORY_NAME_DUPLICATE: '같은 단계에 같은 이름의 카테고리가 있습니다.',
  INVALID_CATEGORY_NAME: '카테고리 이름은 1~30자입니다.',
  CATEGORY_TOO_DEEP: '카테고리는 2단계까지만 둘 수 있습니다.',
  CATEGORY_HAS_CHILDREN: '하위 카테고리가 있는 카테고리는 지울 수 없습니다.',
  CATEGORIES_IN_USE: '글이 있는 카테고리는 지울 수 없습니다. 글을 먼저 옮겨 주세요.',
  INVALID_CATEGORY_TREE: '카테고리 구성이 올바르지 않습니다.',
  CATEGORY_NOT_IN_BLOG: '이 블로그의 카테고리가 아닙니다.',
  INVALID_POST: '제목은 1~150자, 본문은 100,000자 이하입니다.',
  TOO_MANY_IMAGES: '본문의 이미지는 20개까지 넣을 수 있습니다.',
  POST_NOT_FOUND: '글을 찾을 수 없습니다.',
  POST_STATUS_NOT_ALLOWED: '지금 상태에서는 할 수 없는 작업입니다.',
  POST_SLUG_TAKEN: '이미 쓰이는 글 주소입니다.',
  POST_SLUG_FIXED: '발행된 글의 주소는 여기서 바꿀 수 없습니다.',
  INVALID_POST_SLUG: '쓸 수 없는 글 주소입니다.',
  ACCOUNT_NOT_ACTIVE: '지금은 글을 쓸 수 없는 계정입니다.',
  INVALID_REQUEST: '입력한 값을 확인해 주세요.',
};

/** What to tell the person about a failed publishing request. */
export function publishingErrorMessage(
  error: Pick<SerializedError, 'code'> | null | undefined
): string | null {
  if (!error) return null;
  return MESSAGES[error.code] ?? '요청을 처리하지 못했습니다. 다시 시도해 주세요.';
}

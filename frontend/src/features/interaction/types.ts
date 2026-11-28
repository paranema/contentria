// interaction 컨텍스트의 API 응답 및 뷰 모델을 정의합니다. (docs/contexts/interaction/02-implementation.md)

/** 요청한 ID별 좋아요·댓글·구독자 수 맵이며, 집계가 없는 ID는 0이거나 생략됩니다. */
export interface InteractionCounts {
  likes: Record<string, number>;
  comments: Record<string, number>;
  subscribers: Record<string, number>;
}

export interface SubscribedBlog {
  blogId: string;
  slug: string;
  subscribedAt: string;
}

/** 독자에게 표시되는 댓글 항목입니다. 답글이 남아 있는 삭제된 댓글은 본문과 작성자 없이 자리만 유지합니다. */
export interface CommentView {
  commentId: string;
  parentId: string | null;
  commenterId: string | null;
  body: string;
  edited: boolean;
  removed: boolean;
  createdAt: string;
  replies: CommentView[];
}

/** 작성순으로 정렬된 최상위 댓글 페이지(최대 20개)이며, 각 댓글은 모든 답글을 포함합니다. */
export interface CommentPage {
  comments: CommentView[];
  nextCursor: string | null;
}

/** member 컨텍스트의 공개 프로필에서 조합한 댓글 작성자 정보입니다. */
export interface CommentAuthor {
  displayName: string;
  pictureUrl: string | null;
}

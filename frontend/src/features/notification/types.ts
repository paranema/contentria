// notification 컨텍스트의 API 응답 및 화면 표시용 모델을 정의합니다. (docs/contexts/notification/02-implementation.md)
// 백엔드는 게시글·블로그·회원을 ID로만 다루며, 표시 이름은 화면 조립 단계에서 채웁니다. (01-rules 전제 3)

export type NotificationKind = 'NEW_POST' | 'COMMENT_ON_POST' | 'REPLY_TO_COMMENT';

export interface NotificationView {
  notificationId: string;
  kind: NotificationKind;
  postId: string;
  blogId: string;
  commentId: string | null;
  actorId: string | null;
  createdAt: string;
  read: boolean;
}

/** 최신순으로 최대 20개씩 반환되는 알림 페이지 응답입니다. */
export interface NotificationPage {
  notifications: NotificationView[];
  nextCursor: string | null;
}

/** member(작성자 이름)와 publishing(게시글 제목·경로) 정보를 결합한 화면 표시용 알림 항목입니다. */
export interface NotificationEntry extends NotificationView {
  actorName: string | null;
  /** 게시글이 더 이상 공개 상태가 아니면 null이며, 알림함에서도 제목을 노출하지 않습니다. */
  post: { title: string; href: string } | null;
}

export interface NotificationEntries {
  entries: NotificationEntry[];
  nextCursor: string | null;
}

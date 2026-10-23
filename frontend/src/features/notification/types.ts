// The notification context's API (docs/contexts/notification/02-implementation.md). It knows
// posts, blogs and members by id only; the screen names them (01-rules 전제 3).

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

/** Newest first, 20 a page. */
export interface NotificationPage {
  notifications: NotificationView[];
  nextCursor: string | null;
}

/** A notification as the page put it together: who did it (member), which post (publishing). */
export interface NotificationEntry extends NotificationView {
  actorName: string | null;
  /** Null when the post is no longer published: the inbox then names nothing of it. */
  post: { title: string; href: string } | null;
}

export interface NotificationEntries {
  entries: NotificationEntry[];
  nextCursor: string | null;
}

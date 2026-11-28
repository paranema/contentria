'use server';

import { getPublicProfiles } from '@/features/member/api/profile';
import { getNotifications } from '@/features/notification/api/notifications';
import type { NotificationEntries } from '@/features/notification/types';
import { getPublishedTitles } from '@/features/publishing/api/posts';

/**
 * 알림 목록 한 페이지에 발행 중인 게시글 제목과 행위자 이름을 조합합니다. (notification 01-rules 전제 3)
 * 알림함에서 다음 페이지를 직접 요청할 수 있도록 서버 액션으로 제공합니다.
 */
export async function loadNotifications(cursor?: string | null): Promise<NotificationEntries> {
  const page = await getNotifications(cursor);
  const actorIds = page.notifications
    .map((n) => n.actorId)
    .filter((id): id is string => id !== null);
  const [titles, profiles] = await Promise.all([
    getPublishedTitles(page.notifications.map((n) => n.postId)),
    getPublicProfiles(actorIds),
  ]);
  const titleOf = new Map(titles.map((t) => [t.postId, t]));
  const nameOf = new Map(profiles.map((p) => [p.accountId, p.displayName]));
  return {
    entries: page.notifications.map((n) => {
      const title = titleOf.get(n.postId);
      return {
        ...n,
        actorName: n.actorId ? (nameOf.get(n.actorId) ?? null) : null,
        post: title ? { title: title.title, href: `/@${title.blogSlug}/${title.slug}` } : null,
      };
    }),
    nextCursor: page.nextCursor,
  };
}

'use server';

import { getPublicProfiles } from '@/features/member/api/profile';
import { getNotifications } from '@/features/notification/api/notifications';
import type { NotificationEntries } from '@/features/notification/types';
import { getPublishedTitles } from '@/features/publishing/api/posts';

/**
 * A page of the inbox, named (notification 01-rules 전제 3): notification's ids, publishing's
 * titles of posts still published, member's names of whoever acted. A server action, so the inbox
 * can ask for the next page itself.
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

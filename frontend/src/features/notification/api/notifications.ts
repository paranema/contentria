'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import apiServer from '@/shared/api/apiServer';
import { NotificationPage } from '../types';

/** The signed-in member's notifications, a page at a time. */
export async function getNotifications(cursor?: string | null): Promise<NotificationPage> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : '';
  return apiServer.get<NotificationPage>(`/api/me/notifications${query}`);
}

/** For the bell, asked every so often (01-rules 전제 2). Null for someone not signed in. */
export async function getUnreadCount(): Promise<number | null> {
  try {
    const answer = await apiServer.get<{ count: number }>('/api/me/notifications/unread-count', {
      shouldRedirectOn401: false,
    });
    return answer.count;
  } catch {
    return null;
  }
}

export async function markReadAction(notificationId: string): Promise<ActionResult<void>> {
  try {
    await apiServer.post<void>(
      `/api/me/notifications/${encodeURIComponent(notificationId)}/read`,
      {}
    );
    return { success: true, data: undefined };
  } catch (error) {
    return toActionError(error);
  }
}

export async function markAllReadAction(): Promise<ActionResult<void>> {
  try {
    await apiServer.post<void>('/api/me/notifications/read-all', {});
    return { success: true, data: undefined };
  } catch (error) {
    return toActionError(error);
  }
}

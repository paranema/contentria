'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import apiServer from '@/shared/api/apiServer';
import { NotificationPage } from '../types';

/** 로그인한 회원의 알림 목록을 커서 기반 페이지 단위로 조회합니다. */
export async function getNotifications(cursor?: string | null): Promise<NotificationPage> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : '';
  return apiServer.get<NotificationPage>(`/api/me/notifications${query}`);
}

/** 헤더 알림 벨에서 주기적으로 폴링하는 읽지 않은 알림 수를 반환합니다. 비로그인 상태면 null을 반환합니다. (01-rules 전제 2) */
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

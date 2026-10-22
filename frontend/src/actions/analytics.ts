'use server';

import apiServer from '@/shared/api/apiServer';
import { TrackVisitPayload } from '@/types/api/analytics';
import { headers } from 'next/headers';

export async function trackVisitAction(payload: TrackVisitPayload): Promise<void> {
  try {
    const headersList = await headers();
    const userAgent = headersList.get('user-agent') || '';
    const xForwardedFor = headersList.get('x-forwarded-for') || '';
    const realIp = headersList.get('x-real-ip') || '';

    // requireAuth: true는 "토큰이 있으면 첨부"로 동작한다(없으면 헤더 생략, 익명 진행).
    // 백엔드가 방문자를 식별해 블로그 소유자 본인의 방문을 통계에서 제외하기 위함.
    // 만료 토큰이어도 JWT 필터가 익명으로 우아하게 진행하므로 비콘이 깨지지 않는다.
    await apiServer.post<void>('/api/analytics/visit', payload, {
      requireAuth: true,
      shouldRedirectOn401: false,
      headers: {
        'User-Agent': userAgent,
        'X-Forwarded-For': xForwardedFor || realIp,
      },
    });
  } catch (error) {
    console.error('Failed to track visit:', error);
  }
}

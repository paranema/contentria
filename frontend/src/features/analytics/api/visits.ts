'use server';

import apiServer from '@/shared/api/apiServer';
import { headers } from 'next/headers';
import { VisitRequest } from '../types';

/**
 * 블로그 홈 또는 게시글 방문을 기록합니다. (analytics 01-rules 흐름 1)
 * Next.js 서버에서 API를 호출하므로 독자의 IP와 브라우저 정보를 전달 헤더에 담아 보내며,
 * 로그인 세션이 있으면 소유자 본인의 방문은 집계에서 제외됩니다.
 * 방문 기록 실패가 페이지 렌더링을 막지 않도록 예외를 삼킵니다.
 */
export async function recordVisitAction(request: VisitRequest): Promise<void> {
  try {
    const incoming = await headers();
    const forwarded = incoming.get('x-forwarded-for') || incoming.get('x-real-ip') || '';
    await apiServer.post<void>('/api/analytics/visits', request, {
      shouldRedirectOn401: false,
      headers: {
        'User-Agent': incoming.get('user-agent') || '',
        ...(forwarded ? { 'X-Forwarded-For': forwarded } : {}),
      },
    });
  } catch (error) {
    console.error('Failed to record a visit:', error);
  }
}

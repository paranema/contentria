'use server';

import apiServer from '@/shared/api/apiServer';
import { headers } from 'next/headers';
import { VisitRequest } from '../types';

/**
 * Analytics 01-rules 흐름 1. The Next.js server calls the API, so the reader's address and browser
 * travel in the forwarding headers; the API trusts them only from a private address like ours.
 * The session is sent when there is one, so the owner's own visits are left out. Never fails the
 * page: a visit that was not recorded is only a number off.
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

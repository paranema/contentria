import { PATHS } from '@/shared/constants/paths';
import { ApiError } from '@/shared/api/apiError';
import { relaySessionCookies } from '@/shared/api/sessionCookies';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';

/** The API as the Next.js server reaches it; in a cluster, its in-cluster address. */
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

type FetchOptions = RequestInit & {
  /** Sends the access token from the browser's cookie. Default true. */
  requireAuth?: boolean;
  /** Sends the browser to the login page on a 401. Default true. */
  shouldRedirectOn401?: boolean;
  /**
   * Passes the session cookies the API sets on to the browser: for logging in, out and the like.
   * Only from a Server Action (see relaySessionCookies).
   */
  relaySessionCookies?: boolean;
};

export const apiServer = {
  get: <T>(url: string, options?: FetchOptions) =>
    fetchExtended<T>(url, { ...options, method: 'GET' }),

  post: <T>(url: string, body: unknown, options?: FetchOptions) =>
    fetchExtended<T>(url, { ...options, method: 'POST', body: JSON.stringify(body) }),

  put: <T>(url: string, body: unknown, options?: FetchOptions) =>
    fetchExtended<T>(url, { ...options, method: 'PUT', body: JSON.stringify(body) }),

  patch: <T>(url: string, body: unknown, options?: FetchOptions) =>
    fetchExtended<T>(url, { ...options, method: 'PATCH', body: JSON.stringify(body) }),

  delete: <T>(url: string, options?: FetchOptions) =>
    fetchExtended<T>(url, { ...options, method: 'DELETE' }),
};

async function fetchExtended<T>(url: string, options: FetchOptions = {}): Promise<T> {
  const {
    requireAuth = true,
    shouldRedirectOn401 = true,
    relaySessionCookies: relay = false,
    headers: customHeaders,
    ...rest
  } = options;

  const headers = new Headers(customHeaders);
  headers.set('Content-Type', 'application/json');
  if (requireAuth) {
    const accessToken = (await cookies()).get('accessToken')?.value;
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(`${API_BASE_URL}${url}`, { ...rest, headers });

  // Before anything else: a failed login still clears cookies, a logout answers 204 with them expired.
  if (relay) await relaySessionCookies(response);

  if (response.status === 401 && requireAuth && shouldRedirectOn401) {
    redirect(`${PATHS.LOGIN}?alert=session_expired`); // Throws NEXT_REDIRECT, which Next.js handles.
  }

  if (!response.ok) {
    const body = await response.json().catch(() => undefined);
    throw ApiError.from(response.status, body);
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text);
}

export default apiServer;

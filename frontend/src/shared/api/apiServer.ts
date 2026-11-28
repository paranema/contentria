import { PATHS } from '@/shared/constants/paths';
import { getApiBaseUrl } from '@/shared/api/apiBase';
import { ApiError } from '@/shared/api/apiError';
import { relaySessionCookies } from '@/shared/api/sessionCookies';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';

type FetchOptions = RequestInit & {
  /** 브라우저 쿠키의 액세스 토큰을 `Authorization` 헤더에 담아 전송할지 여부입니다(기본값: `true`). */
  requireAuth?: boolean;
  /** 401 응답 수신 시 로그인 페이지로 리다이렉트할지 여부입니다(기본값: `true`). */
  shouldRedirectOn401?: boolean;
  /**
   * 로그인·로그아웃 등 백엔드 응답이 설정한 세션 쿠키를 브라우저로 전달할지 여부입니다.
   * 쿠키 쓰기가 허용된 Server Action 또는 Route Handler에서만 사용해야 합니다(`relaySessionCookies` 참조).
   */
  relaySessionCookies?: boolean;
  /**
   * 브라우저의 리프레시 토큰 쿠키를 백엔드로 전달할지 여부입니다.
   * 서버 간 호출은 기본적으로 쿠키를 자동 전달하지 않으므로, 로그아웃이나 토큰 갱신 요청에서만 활성화합니다.
   */
  sendRefreshToken?: boolean;
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
    sendRefreshToken = false,
    headers: customHeaders,
    ...rest
  } = options;

  const headers = new Headers(customHeaders);
  headers.set('Content-Type', 'application/json');
  if (requireAuth || sendRefreshToken) {
    const store = await cookies();
    const accessToken = store.get('accessToken')?.value;
    if (requireAuth && accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
    const refreshToken = store.get('refreshToken')?.value;
    if (sendRefreshToken && refreshToken) headers.set('Cookie', `refreshToken=${refreshToken}`);
  }

  const response = await fetch(`${getApiBaseUrl()}${url}`, { ...rest, headers });

  // 로그인 실패 시의 쿠키 초기화나 로그아웃(204)의 쿠키 만료 처리를 누락하지 않도록 상태 코드 분기 전에 먼저 반영합니다.
  if (relay) await relaySessionCookies(response);

  if (response.status === 401 && requireAuth && shouldRedirectOn401) {
    redirect(`${PATHS.LOGIN}?alert=session_expired`); // Next.js가 처리하는 NEXT_REDIRECT 예외를 발생시킵니다.
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

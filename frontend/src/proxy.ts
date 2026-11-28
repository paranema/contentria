import { NextRequest, NextResponse } from 'next/server';
import { getApiBaseUrl } from './shared/api/apiBase';
import { ParsedCookie, parseSetCookie } from './shared/api/setCookie';
import { PATHS } from './shared/constants/paths';

/** 백엔드가 세션을 전달할 때 사용하는 쿠키 이름 목록입니다(백엔드 `SessionCookieNames` 대응). */
const SESSION_COOKIES: string[] = ['accessToken', 'refreshToken'];

/** 토큰 갱신 응답에서 추출해 브라우저 응답에 설정할 세션 쿠키 목록입니다. */
type RefreshedSession = ParsedCookie[];

// 동일한 리프레시 토큰으로 동시에 들어온 갱신 요청을 단일 백엔드 호출로 병합합니다.
// 병합 범위는 단일 Node.js 프로세스(파드 단위)이며, 파드 간 중복 방지는 인그레스 세션 고정(sticky session)과
// 백엔드의 비관적 락 및 유예 캐시(grace cache)에 위임합니다.
const inflightRefresh = new Map<string, Promise<RefreshedSession>>();

/**
 * 백엔드는 리프레시 토큰을 회전(rotation)한 뒤 응답 본문이 아닌 `Set-Cookie` 헤더로
 * 두 세션 쿠키를 내려줍니다(백엔드 `AuthController.refresh` 참조).
 */
async function fetchRefreshedSession(refreshToken: string): Promise<RefreshedSession> {
  const existing = inflightRefresh.get(refreshToken);
  if (existing) return existing;

  const promise = (async (): Promise<RefreshedSession> => {
    const refreshResponse = await fetch(`${getApiBaseUrl()}/api/auth/refresh`, {
      method: 'POST',
      headers: { Cookie: `refreshToken=${refreshToken}` },
    });

    if (!refreshResponse.ok) {
      throw new Error('Refresh token invalid or expired');
    }
    const cookies = refreshResponse.headers
      .getSetCookie()
      .map(parseSetCookie)
      .filter(
        (cookie): cookie is ParsedCookie => cookie !== null && SESSION_COOKIES.includes(cookie.name)
      );
    if (cookies.length !== SESSION_COOKIES.length) {
      throw new Error('Refresh response is missing the session cookies');
    }
    return cookies;
  })().finally(() => {
    inflightRefresh.delete(refreshToken);
  });

  inflightRefresh.set(refreshToken, promise);
  return promise;
}

export default async function proxy(request: NextRequest) {
  console.log('[Proxy] executed for path:', request.nextUrl.pathname);

  const { pathname } = request.nextUrl;

  const accessToken = request.cookies.get('accessToken')?.value;
  const refreshToken = request.cookies.get('refreshToken')?.value;

  const isAuthPage = pathname.startsWith('/login') || pathname.startsWith('/signup');
  const isProtectedPage = pathname.startsWith('/dashboard');

  const isSessionExpired = request.nextUrl.searchParams.get('alert') === 'session_expired';

  if (isAuthPage) {
    if (isSessionExpired) {
      console.log('[Proxy] Session expired signal detected. Clearing cookies.');
      const response = NextResponse.next();
      response.cookies.delete('accessToken');
      response.cookies.delete('refreshToken');
      return response;
    }

    if (accessToken || refreshToken) {
      console.log('[Proxy] User already logged in. Redirecting to dashboard.');
      return NextResponse.redirect(new URL('/dashboard', request.url));
    }
    return NextResponse.next();
  }

  if (!accessToken && refreshToken) {
    const refreshResponse = await tryRefreshTokens(request, refreshToken);
    if (refreshResponse.status === 307) {
      return refreshResponse;
    }
    if (isProtectedPage) {
      return redirectToLogin(request, pathname);
    }
    return refreshResponse;
  }

  if (isProtectedPage && !accessToken) {
    console.log('[Proxy] No access token for protected page. Redirecting to login.');
    return redirectToLogin(request, pathname);
  }

  console.log('[Proxy] Completed checks. Proceeding to route handler.');
  return NextResponse.next();
}

async function tryRefreshTokens(request: NextRequest, refreshToken: string): Promise<NextResponse> {
  console.log('[Proxy] accessToken expired. Attempting refresh.');

  try {
    const session = await fetchRefreshedSession(refreshToken);

    console.log('[Proxy] Refresh successful. Reloading...');
    const response = NextResponse.redirect(request.url);
    session.forEach(({ name, value, ...options }) => response.cookies.set(name, value, options));
    return response;
  } catch (error) {
    console.error('[Proxy] Refresh failed. Clearing cookies.', error);
    const response = NextResponse.next();
    response.cookies.delete('accessToken');
    response.cookies.delete('refreshToken');
    return response;
  }
}

function redirectToLogin(request: NextRequest, fromPath: string) {
  const loginUrl = new URL(PATHS.LOGIN, request.url);
  loginUrl.searchParams.set('redirect', fromPath);

  const response = NextResponse.redirect(loginUrl);
  response.cookies.delete('accessToken');
  response.cookies.delete('refreshToken');
  return response;
}

export const config = {
  /**
   * 정적 자산(`_next/static`, `_next/image`, `favicon.ico`, `icons`, 확장자가 있는 파일)과
   * 라우터 프리페치 요청을 제외한 모든 경로에 프록시를 적용합니다.
   */
  matcher: [
    {
      source: '/((?!_next/static|_next/image|favicon.ico|icons|.*\\..*).*)',
      missing: [
        { type: 'header', key: 'next-router-prefetch' },
        { type: 'header', key: 'purpose', value: 'prefetch' },
      ],
    },
  ],
};

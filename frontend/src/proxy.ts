import { NextRequest, NextResponse } from 'next/server';
import { ParsedCookie, parseSetCookie } from './shared/api/setCookie';
import { PATHS } from './shared/constants/paths';

/** The cookies the API carries a session in (backend: `SessionCookieNames`). */
const SESSION_COOKIES: string[] = ['accessToken', 'refreshToken'];

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

/** The session cookies the API sets on a refresh, ready to set on the browser's response. */
type RefreshedSession = ParsedCookie[];

// Coalesces concurrent refresh attempts for the same refresh token into a single
// backend call. Scope is the Node.js process (one entry per pod). Cross-pod
// dedup relies on ingress sticky sessions; the backend's pessimistic lock +
// grace cache is the ultimate safety net.
const inflightRefresh = new Map<string, Promise<RefreshedSession>>();

/**
 * The API rotates the refresh token and answers with both session cookies in `Set-Cookie`,
 * not in the body (backend AuthController.refresh).
 */
async function fetchRefreshedSession(refreshToken: string): Promise<RefreshedSession> {
  const existing = inflightRefresh.get(refreshToken);
  if (existing) return existing;

  const promise = (async (): Promise<RefreshedSession> => {
    const refreshResponse = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
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
   * Match all request paths except for the ones starting with:
   *   1. api
   *   2. _next/static
   *   3. _next/image
   *   4. favicon.ico
   *   5. icons
   *   6. any file with an extension (e.g., .css, .js, .png, etc.)
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

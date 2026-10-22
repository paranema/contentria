import { cookies } from 'next/headers';
import { parseSetCookie } from './setCookie';

/** The cookies the API carries a session in (backend: `SessionCookieNames`). */
export const SESSION_COOKIES = ['accessToken', 'refreshToken'] as const;

/**
 * Passes the session cookies the API set on its response on to the browser. The API sets them
 * itself and keeps the tokens out of the body, but a Server Action calls it from the Next.js
 * server, so the browser would never see them otherwise. Callable only where Next.js lets cookies
 * be written: a Server Action or a Route Handler.
 */
export async function relaySessionCookies(response: Response): Promise<void> {
  const store = await cookies();
  for (const header of response.headers.getSetCookie()) {
    const cookie = parseSetCookie(header);
    if (cookie && (SESSION_COOKIES as readonly string[]).includes(cookie.name)) {
      const { name, value, ...options } = cookie;
      store.set(name, value, options);
    }
  }
}

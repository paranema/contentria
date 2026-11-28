import { cookies } from 'next/headers';
import { parseSetCookie } from './setCookie';

/** 백엔드가 세션을 전달할 때 사용하는 쿠키 이름 목록입니다(백엔드 `SessionCookieNames` 대응). */
export const SESSION_COOKIES = ['accessToken', 'refreshToken'] as const;

/**
 * 백엔드 응답의 `Set-Cookie` 헤더에 담긴 세션 쿠키를 브라우저로 중계합니다.
 * 백엔드는 토큰을 응답 본문에 노출하지 않고 쿠키로만 설정하므로, Next.js 서버에서 호출하는
 * Server Action이나 Route Handler가 이를 브라우저 쿠키 저장소에 다시 기록해야 합니다.
 * 쿠키 쓰기가 허용된 Server Action 또는 Route Handler에서만 호출할 수 있습니다.
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

/** `Set-Cookie` 헤더를 파싱해 Next.js 쿠키 저장소에 바로 전달할 수 있도록 변환한 쿠키 객체입니다. */
export interface ParsedCookie {
  name: string;
  value: string;
  path?: string;
  maxAge?: number;
  expires?: Date;
  httpOnly: boolean;
  secure: boolean;
  sameSite?: 'lax' | 'strict' | 'none';
}

/** 단일 `Set-Cookie` 헤더 문자열을 파싱하며, 유효한 `name=value` 쌍이 없으면 `null`을 반환합니다. */
export function parseSetCookie(header: string): ParsedCookie | null {
  const [pair, ...attributes] = header.split(';').map((part) => part.trim());
  const separator = pair.indexOf('=');
  if (separator <= 0) return null;

  const cookie: ParsedCookie = {
    name: pair.slice(0, separator),
    value: pair.slice(separator + 1),
    httpOnly: false,
    secure: false,
  };
  for (const attribute of attributes) {
    const [rawKey, ...rest] = attribute.split('=');
    const key = rawKey.toLowerCase();
    const value = rest.join('=');
    if (key === 'path') cookie.path = value;
    else if (key === 'max-age') cookie.maxAge = Number(value);
    else if (key === 'expires') cookie.expires = new Date(value);
    else if (key === 'httponly') cookie.httpOnly = true;
    else if (key === 'secure') cookie.secure = true;
    else if (key === 'samesite') {
      const sameSite = value.toLowerCase();
      if (sameSite === 'lax' || sameSite === 'strict' || sameSite === 'none')
        cookie.sameSite = sameSite;
    }
  }
  return cookie;
}

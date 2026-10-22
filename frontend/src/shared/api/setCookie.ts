/** A cookie as a `Set-Cookie` header describes it, in the shape Next.js's cookie stores take. */
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

/** Parses one `Set-Cookie` header value; null when it has no `name=value`. */
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

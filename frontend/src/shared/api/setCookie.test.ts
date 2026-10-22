import { describe, expect, it } from 'vitest';
import { parseSetCookie } from './setCookie';

describe('parseSetCookie', () => {
  it('reads a session cookie as the API sets it', () => {
    const cookie = parseSetCookie(
      'accessToken=a.b.c; Path=/; Max-Age=900; Expires=Wed, 21 Oct 2026 12:15:00 GMT; Secure; HttpOnly; SameSite=Lax'
    );

    expect(cookie).toEqual({
      name: 'accessToken',
      value: 'a.b.c',
      path: '/',
      maxAge: 900,
      expires: new Date('Wed, 21 Oct 2026 12:15:00 GMT'),
      httpOnly: true,
      secure: true,
      sameSite: 'lax',
    });
  });

  it('reads an expired cookie, as a logout sends it', () => {
    const cookie = parseSetCookie('refreshToken=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax');

    expect(cookie).toMatchObject({ name: 'refreshToken', value: '', maxAge: 0, secure: false });
  });

  it('keeps an equals sign inside the value', () => {
    expect(parseSetCookie('oauth2AuthRequest=abc==; Path=/')?.value).toBe('abc==');
  });

  it('gives null for a header without name=value', () => {
    expect(parseSetCookie('=value; Path=/')).toBeNull();
    expect(parseSetCookie('garbage')).toBeNull();
  });
});

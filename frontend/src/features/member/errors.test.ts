import { describe, expect, it } from 'vitest';
import { isAttemptOver, accountErrorMessage, memberErrorMessage } from './errors';

describe('memberErrorMessage', () => {
  it('names the ways an already registered address signs in', () => {
    expect(
      memberErrorMessage({ code: 'EMAIL_ALREADY_REGISTERED', providers: ['EMAIL', 'GOOGLE'] })
    ).toBe('This email is already registered. Sign in with email and password or Google.');
    expect(memberErrorMessage({ code: 'EMAIL_ALREADY_REGISTERED' })).toBe(
      'This email is already registered. Please sign in.'
    );
  });

  it('has a message for each code the API sends and a fallback for the rest', () => {
    expect(memberErrorMessage({ code: 'INVALID_CREDENTIALS' })).toBe(
      'Email or password does not match.'
    );
    expect(memberErrorMessage({ code: 'SOCIAL_EMAIL_MISSING' })).toContain('Google');
    expect(memberErrorMessage({ code: 'SOMETHING_NEW' })).toBe(
      'Something went wrong. Please try again.'
    );
    expect(memberErrorMessage(null)).toBeNull();
  });
});

describe('isAttemptOver', () => {
  it('is over when the code is locked or expired, not when it is only wrong', () => {
    expect(isAttemptOver({ code: 'VERIFICATION_LOCKED' })).toBe(true);
    expect(isAttemptOver({ code: 'VERIFICATION_EXPIRED' })).toBe(true);
    expect(isAttemptOver({ code: 'VERIFICATION_MISMATCH' })).toBe(false);
  });
});

describe('accountErrorMessage', () => {
  it('words failures for the settings screen, not the login pages', () => {
    expect(accountErrorMessage({ code: 'VERIFICATION_MISMATCH' })).toBe(
      '코드가 맞지 않습니다. 확인하고 다시 넣어 주세요.'
    );
    expect(accountErrorMessage({ code: 'SOCIAL_LOGIN_FAILED' })).toBe(
      'Google 연동에 실패했습니다. 다시 시도해 주세요.'
    );
    expect(accountErrorMessage({ code: 'SOCIAL_IDENTITY_TAKEN' })).toBe(
      '그 Google 계정은 다른 회원과 연동되어 있습니다.'
    );
    expect(accountErrorMessage({ code: 'SOMETHING_NEW' })).toBe(
      '문제가 생겼습니다. 다시 시도해 주세요.'
    );
  });
});

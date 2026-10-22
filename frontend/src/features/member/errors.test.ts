import { describe, expect, it } from 'vitest';
import { isAttemptOver, memberErrorMessage } from './errors';

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

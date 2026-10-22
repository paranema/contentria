import { SerializedError } from '@/shared/api/actionResult';

/** Codes the member API answers with that the screens act on, not only show. */
export const MEMBER_ERRORS = {
  RECAPTCHA_CHALLENGE_REQUIRED: 'RECAPTCHA_CHALLENGE_REQUIRED',
  VERIFICATION_MISMATCH: 'VERIFICATION_MISMATCH',
  VERIFICATION_LOCKED: 'VERIFICATION_LOCKED',
  VERIFICATION_EXPIRED: 'VERIFICATION_EXPIRED',
} as const;

/** The code attempt is over: only a new one, from the start, can go on. */
export function isAttemptOver(error: Pick<SerializedError, 'code'>): boolean {
  return (
    error.code === MEMBER_ERRORS.VERIFICATION_LOCKED ||
    error.code === MEMBER_ERRORS.VERIFICATION_EXPIRED
  );
}

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Email or password does not match.',
  ACCOUNT_NOT_ACTIVE: 'This account cannot sign in.',
  INVALID_EMAIL: 'Please enter a usable email address.',
  INVALID_REQUEST: 'Please check what you entered.',
  VERIFICATION_MISMATCH: 'The code does not match. Please check it and try again.',
  VERIFICATION_LOCKED: 'Too many wrong codes. Please start again.',
  VERIFICATION_EXPIRED: 'The code has expired. Please start again.',
  RECAPTCHA_CHALLENGE_REQUIRED: 'Please confirm you are not a robot.',
  RECAPTCHA_FAILED: 'The check did not pass. Please try again.',
  RECAPTCHA_UNAVAILABLE: 'Please try again in a moment.',
  DISPLAY_NAME_TAKEN: '다른 회원이 이미 쓰는 이름입니다.',
  INVALID_PROFILE: '쓸 수 없는 이름입니다.',
  // Social sign-in failures arrive as ?error=<code> on the login page.
  SOCIAL_EMAIL_MISSING: 'Google did not share your email address. Please allow it and try again.',
  SOCIAL_EMAIL_NOT_VERIFIED: 'Your Google email address is not verified.',
  SOCIAL_LOGIN_FAILED: 'Signing in with Google failed. Please try again.',
};

const PROVIDER_NAMES: Record<string, string> = { EMAIL: 'email and password', GOOGLE: 'Google' };

/** What to tell the person about a failed member request. */
export function memberErrorMessage(
  error: Pick<SerializedError, 'code' | 'providers'> | null | undefined
): string | null {
  if (!error) return null;
  if (error.code === 'EMAIL_ALREADY_REGISTERED') {
    const ways = (error.providers ?? []).map((provider) => PROVIDER_NAMES[provider] ?? provider);
    return ways.length > 0
      ? `This email is already registered. Sign in with ${ways.join(' or ')}.`
      : 'This email is already registered. Please sign in.';
  }
  return MESSAGES[error.code] ?? 'Something went wrong. Please try again.';
}

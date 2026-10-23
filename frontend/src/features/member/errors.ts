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
  CURRENT_PASSWORD_MISMATCH: '현재 비밀번호가 맞지 않습니다.',
  PASSWORD_NOT_SET: '비밀번호가 없는 계정입니다. 설정 링크를 받아 주세요.',
  PASSWORD_ALREADY_SET: '이미 비밀번호가 있는 계정입니다.',
  EMAIL_IN_USE: '다른 계정이 쓰는 이메일 주소입니다.',
  EMAIL_UNCHANGED: '지금 쓰는 이메일 주소와 같습니다.',
  LAST_CREDENTIAL: '로그인할 방법이 하나도 남지 않아 해제할 수 없습니다.',
  PROVIDER_NOT_LINKED: '연동되지 않은 계정입니다.',
  SOCIAL_IDENTITY_TAKEN: '그 Google 계정은 다른 회원과 연동되어 있습니다.',
  PROVIDER_ALREADY_LINKED: '이미 다른 Google 계정이 연동되어 있습니다.',
  SIGN_IN_REQUIRED: '로그인한 뒤 다시 시도해 주세요.',
  // The password link page is an auth screen, worded like the login pages.
  INVALID_PASSWORD_TOKEN: 'This link is no longer valid. Please ask for a new one.',
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

/**
 * The dashboard is worded in Korean and the login pages in English, so the codes both can show
 * have a second wording here: code checks, entries, and linking Google (`?error=<code>`).
 */
const ACCOUNT_MESSAGES: Record<string, string> = {
  INVALID_EMAIL: '쓸 수 있는 이메일 주소를 넣어 주세요.',
  INVALID_REQUEST: '입력한 내용을 확인해 주세요.',
  VERIFICATION_MISMATCH: '코드가 맞지 않습니다. 확인하고 다시 넣어 주세요.',
  VERIFICATION_LOCKED: '틀린 코드를 너무 많이 넣었습니다. 처음부터 다시 해 주세요.',
  VERIFICATION_EXPIRED: '코드의 유효 시간이 지났습니다. 처음부터 다시 해 주세요.',
  SOCIAL_EMAIL_MISSING:
    'Google이 이메일 주소를 알려 주지 않았습니다. 허용한 뒤 다시 시도해 주세요.',
  SOCIAL_EMAIL_NOT_VERIFIED: 'Google 계정의 이메일 주소가 확인되지 않았습니다.',
  SOCIAL_LOGIN_FAILED: 'Google 연동에 실패했습니다. 다시 시도해 주세요.',
};

/** What the settings screen tells the person about a failed account request. */
export function accountErrorMessage(error: Pick<SerializedError, 'code'>): string {
  return (
    ACCOUNT_MESSAGES[error.code] ?? MESSAGES[error.code] ?? '문제가 생겼습니다. 다시 시도해 주세요.'
  );
}

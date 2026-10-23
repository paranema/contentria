'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import apiServer from '@/shared/api/apiServer';
import { CodeSentResponse, HumanProof, SessionResponse } from '../types';

// docs/contexts/member/01-rules.md, 흐름 3, 4. Changes that end the account's other sessions
// answer with a new session for this browser, which is passed on (relaySessionCookies).
const NEW_SESSION = { relaySessionCookies: true } as const;

async function attempt<T>(call: () => Promise<T>): Promise<ActionResult<T>> {
  try {
    return { success: true, data: await call() };
  } catch (error) {
    return toActionError(error);
  }
}

/** The ways the account signs in: `EMAIL` (a password) and linked providers such as `GOOGLE`. */
export async function getMyCredentials(): Promise<string[]> {
  return (await apiServer.get<{ providers: string[] }>('/api/me/credentials')).providers;
}

export async function unlinkProviderAction(
  provider: string
): Promise<ActionResult<SessionResponse>> {
  return attempt(() =>
    apiServer.delete<SessionResponse>(
      `/api/me/credentials/${encodeURIComponent(provider)}`,
      NEW_SESSION
    )
  );
}

export async function changePasswordAction(
  currentPassword: string,
  newPassword: string
): Promise<ActionResult<SessionResponse>> {
  return attempt(() =>
    apiServer.post<SessionResponse>(
      '/api/me/password',
      { currentPassword, newPassword },
      NEW_SESSION
    )
  );
}

/** A social-only account's first password, set through a link mailed to it. */
export async function requestPasswordSetupLinkAction(): Promise<ActionResult<void>> {
  return attempt(() => apiServer.post<void>('/api/me/password/setup-link', {}));
}

/** A forgotten password: always answers alike, whether or not an account uses the address. */
export async function requestPasswordResetLinkAction(
  request: { email: string } & HumanProof
): Promise<ActionResult<void>> {
  return attempt(() =>
    apiServer.post<void>('/api/auth/password/reset-link', request, { requireAuth: false })
  );
}

/** Spends a setup or reset link; the account's other sessions end and this one starts. */
export async function resetPasswordAction(
  token: string,
  newPassword: string
): Promise<ActionResult<SessionResponse>> {
  return attempt(() =>
    apiServer.post<SessionResponse>(
      '/api/auth/password/reset',
      { token, newPassword },
      { requireAuth: false, relaySessionCookies: true }
    )
  );
}

/** Codes go to the current address and to the new one; both must come back (01-rules 흐름 4). */
export async function startEmailChangeAction(
  newEmail: string
): Promise<ActionResult<CodeSentResponse>> {
  return attempt(() =>
    apiServer.post<CodeSentResponse>('/api/me/email-change/start', { newEmail })
  );
}

export async function completeEmailChangeAction(
  attemptId: string,
  currentAddressCode: string,
  newAddressCode: string
): Promise<ActionResult<SessionResponse>> {
  return attempt(() =>
    apiServer.post<SessionResponse>(
      '/api/me/email-change/complete',
      { attemptId, currentAddressCode, newAddressCode },
      NEW_SESSION
    )
  );
}

/** A code to the account's address confirms the withdrawal. */
export async function startWithdrawalAction(): Promise<ActionResult<CodeSentResponse>> {
  return attempt(() => apiServer.post<CodeSentResponse>('/api/me/withdrawal/start', {}));
}

/** The account is gone; the API expires both cookies, and the screen sends the browser home. */
export async function completeWithdrawalAction(
  attemptId: string,
  code: string
): Promise<ActionResult<void>> {
  return attempt(() =>
    apiServer.post<void>(
      '/api/me/withdrawal/complete',
      { attemptId, code },
      { relaySessionCookies: true }
    )
  );
}

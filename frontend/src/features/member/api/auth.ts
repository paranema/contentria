'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import apiServer from '@/shared/api/apiServer';
import { redirect } from 'next/navigation';
import {
  CodeSentResponse,
  CompleteCodeLoginRequest,
  CompleteSignUpRequest,
  LoginRequest,
  SessionResponse,
  StartCodeLoginRequest,
  StartSignUpRequest,
} from '../types';

// Every call that starts a session passes the cookies the API sets on to the browser
// (shared/api/sessionCookies). None of them is authenticated, so none sends a token.
const SESSION = { requireAuth: false, relaySessionCookies: true } as const;
const ANONYMOUS = { requireAuth: false } as const;

async function attempt<T>(call: () => Promise<T>): Promise<ActionResult<T>> {
  try {
    return { success: true, data: await call() };
  } catch (error) {
    return toActionError(error);
  }
}

/** 흐름 1의 1, 2번: mails a sign-up code. */
export async function startSignUpAction(
  request: StartSignUpRequest
): Promise<ActionResult<CodeSentResponse>> {
  return attempt(() =>
    apiServer.post<CodeSentResponse>('/api/auth/sign-up/start', request, ANONYMOUS)
  );
}

/** 흐름 1의 3번: the code and the password; the account is made and signed in. */
export async function completeSignUpAction(
  request: CompleteSignUpRequest
): Promise<ActionResult<SessionResponse>> {
  return attempt(() =>
    apiServer.post<SessionResponse>('/api/auth/sign-up/complete', request, SESSION)
  );
}

export async function loginWithPasswordAction(
  request: LoginRequest
): Promise<ActionResult<SessionResponse>> {
  return attempt(() => apiServer.post<SessionResponse>('/api/auth/login', request, SESSION));
}

/** 흐름 6의 1, 2번: mails a login code. Answers alike whether or not an account uses the address. */
export async function startCodeLoginAction(
  request: StartCodeLoginRequest
): Promise<ActionResult<CodeSentResponse>> {
  return attempt(() =>
    apiServer.post<CodeSentResponse>('/api/auth/login/code/start', request, ANONYMOUS)
  );
}

/** 흐름 6의 3번 */
export async function completeCodeLoginAction(
  request: CompleteCodeLoginRequest
): Promise<ActionResult<SessionResponse>> {
  return attempt(() =>
    apiServer.post<SessionResponse>('/api/auth/login/code/complete', request, SESSION)
  );
}

/** This browser only. The API answers 204 with both cookies expired, whatever the session's state. */
export async function logoutAction() {
  try {
    await apiServer.post(
      '/api/auth/logout',
      {},
      { ...SESSION, sendRefreshToken: true, shouldRedirectOn401: false }
    );
  } catch (error) {
    console.error('Logout failed on the API:', error);
  }
  redirect('/');
}

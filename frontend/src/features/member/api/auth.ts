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

// 세션을 시작하는 비인증 요청은 API가 발급한 세션 쿠키를 브라우저로 전달합니다. (shared/api/sessionCookies)
const SESSION = { requireAuth: false, relaySessionCookies: true } as const;
const ANONYMOUS = { requireAuth: false } as const;

async function attempt<T>(call: () => Promise<T>): Promise<ActionResult<T>> {
  try {
    return { success: true, data: await call() };
  } catch (error) {
    return toActionError(error);
  }
}

/** 회원가입용 인증 코드를 이메일로 발송합니다. (흐름 1의 1, 2번) */
export async function startSignUpAction(
  request: StartSignUpRequest
): Promise<ActionResult<CodeSentResponse>> {
  return attempt(() =>
    apiServer.post<CodeSentResponse>('/api/auth/sign-up/start', request, ANONYMOUS)
  );
}

/** 인증 코드와 비밀번호를 검증하여 계정을 생성하고 로그인 세션을 시작합니다. (흐름 1의 3번) */
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

/** 로그인용 일회용 코드를 이메일로 발송하며, 계정 존재 여부와 무관하게 동일하게 응답합니다. (흐름 6의 1, 2번) */
export async function startCodeLoginAction(
  request: StartCodeLoginRequest
): Promise<ActionResult<CodeSentResponse>> {
  return attempt(() =>
    apiServer.post<CodeSentResponse>('/api/auth/login/code/start', request, ANONYMOUS)
  );
}

/** 일회용 인증 코드를 검증하고 로그인 세션을 시작합니다. (흐름 6의 3번) */
export async function completeCodeLoginAction(
  request: CompleteCodeLoginRequest
): Promise<ActionResult<SessionResponse>> {
  return attempt(() =>
    apiServer.post<SessionResponse>('/api/auth/login/code/complete', request, SESSION)
  );
}

/** 현재 브라우저의 세션만 로그아웃합니다. 세션 상태와 무관하게 API가 두 쿠키를 만료시켜 응답합니다. */
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

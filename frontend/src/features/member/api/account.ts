'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import apiServer from '@/shared/api/apiServer';
import { CodeSentResponse, HumanProof, SessionResponse } from '../types';

// 다른 기기의 세션을 종료하는 변경 요청은 현재 브라우저용 새 세션을 응답하며 이를 쿠키로 전달합니다. (docs/contexts/member/01-rules.md, 흐름 3, 4)
const NEW_SESSION = { relaySessionCookies: true } as const;

async function attempt<T>(call: () => Promise<T>): Promise<ActionResult<T>> {
  try {
    return { success: true, data: await call() };
  } catch (error) {
    return toActionError(error);
  }
}

/** 계정에 등록된 로그인 수단(`EMAIL` 비밀번호 및 `GOOGLE` 등 연동 제공자) 목록을 조회합니다. */
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

/** 소셜 전용 계정의 첫 비밀번호를 설정할 수 있도록 이메일로 링크를 발송합니다. */
export async function requestPasswordSetupLinkAction(): Promise<ActionResult<void>> {
  return attempt(() => apiServer.post<void>('/api/me/password/setup-link', {}));
}

/** 비밀번호 재설정 링크를 발송합니다. 계정 존재 여부와 무관하게 동일하게 응답합니다. */
export async function requestPasswordResetLinkAction(
  request: { email: string } & HumanProof
): Promise<ActionResult<void>> {
  return attempt(() =>
    apiServer.post<void>('/api/auth/password/reset-link', request, { requireAuth: false })
  );
}

/** 설정 또는 재설정 링크 토큰으로 새 비밀번호를 지정하고, 기존 세션을 종료한 뒤 현재 세션을 시작합니다. */
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

/** 현재 이메일과 새 이메일 모두로 확인 코드를 발송하며, 두 코드를 모두 제출해야 변경이 완료됩니다. (01-rules 흐름 4) */
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

/** 계정 이메일로 회원 탈퇴 확인 코드를 발송합니다. */
export async function startWithdrawalAction(): Promise<ActionResult<CodeSentResponse>> {
  return attempt(() => apiServer.post<CodeSentResponse>('/api/me/withdrawal/start', {}));
}

/** 회원 탈퇴를 완료하고 API가 만료시킨 세션 쿠키를 브라우저에 반영합니다. */
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

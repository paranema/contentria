// The member context's API, as backend/contexts/member answers it (docs/contexts/member/02-implementation.md).

/** The tokens the human check sends with the requests that need it (01-rules, 사람 확인). */
export interface HumanProof {
  recaptchaV3Token?: string;
  recaptchaV2Token?: string;
}

export interface StartSignUpRequest extends HumanProof {
  email: string;
}

export interface CompleteSignUpRequest {
  attemptId: string;
  code: string;
  password: string;
}

export interface LoginRequest extends HumanProof {
  email: string;
  password: string;
}

export interface StartCodeLoginRequest extends HumanProof {
  email: string;
}

export interface CompleteCodeLoginRequest {
  attemptId: string;
  code: string;
}

/** Every request that mails a code answers with the attempt to present the code with. */
export interface CodeSentResponse {
  attemptId: string;
}

/** The session itself travels in cookies; the body only says whose and until when. */
export interface SessionResponse {
  accountId: string;
  accessTokenExpiresAt: string;
}

export interface MyProfile {
  accountId: string;
  email: string;
  displayName: string;
  pictureUrl: string | null;
}

export interface RenameProfileRequest {
  displayName: string;
}

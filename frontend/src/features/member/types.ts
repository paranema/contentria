// backend/contexts/member API와 주고받는 요청·응답 타입입니다. (docs/contexts/member/02-implementation.md)

/** 사람 확인이 필요한 요청에 함께 전달하는 reCAPTCHA 토큰입니다. (01-rules, 사람 확인) */
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

/** 인증 코드 발송 요청의 응답으로, 이후 코드 검증 시 함께 제출할 시도 식별자입니다. */
export interface CodeSentResponse {
  attemptId: string;
}

/** 세션 자체는 쿠키로 전달되며, 응답 본문에는 계정 식별자와 액세스 토큰 만료 시각만 담깁니다. */
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

/** 이메일이나 계정 상태를 제외한 회원의 공개 프로필 정보입니다. (01-rules 흐름 5, 공개 범위) */
export interface PublicProfile {
  accountId: string;
  displayName: string;
  pictureUrl: string | null;
}

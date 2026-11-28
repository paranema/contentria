/** 보이지 않는 reCAPTCHA v3 검증에 사용하는 액션 이름입니다. API는 토큰이 해당 요청 용도로 발급되었는지 확인합니다. */
export const RECAPTCHA_ACTIONS = {
  SIGN_UP: 'sign_up',
  LOGIN: 'login',
  LOGIN_CODE: 'login_code',
  PASSWORD_RESET: 'password_reset',
} as const;

export type RecaptchaAction = (typeof RECAPTCHA_ACTIONS)[keyof typeof RECAPTCHA_ACTIONS];

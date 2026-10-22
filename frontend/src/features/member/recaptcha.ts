/** The actions the invisible check runs for; the API checks a token was made for its own request. */
export const RECAPTCHA_ACTIONS = {
  SIGN_UP: 'sign_up',
  LOGIN: 'login',
  LOGIN_CODE: 'login_code',
  PASSWORD_RESET: 'password_reset',
} as const;

export type RecaptchaAction = (typeof RECAPTCHA_ACTIONS)[keyof typeof RECAPTCHA_ACTIONS];

import { PATHS } from '@/shared/constants/paths';

export const ACCESS_TOKEN_COOKIE_NAME = 'access_token';
export const REFRESH_COOKIE_NAME = 'refresh_token';
export const DEFAULT_LOGGED_IN_REDIRECT_URL = PATHS.DASHBOARD;
export const DEFAULT_LOGGED_OUT_REDIRECT_URL = PATHS.LOGIN;
export const REFRESH_URL = '/api/auth/refresh';
export const RECAPTCHA_SIGN_UP_ACTION = 'signup_initiate';
export const RECAPTCHA_LOGIN_WITH_PASSWORD_ACTION = 'login_with_password';
export const RECAPTCHA_SEND_OTP_ACTION = 'send_otp_code';

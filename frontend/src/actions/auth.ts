'use server';

import apiServer from '@/shared/api/apiServer';
import {
  InitiateSignUpPayload,
  InitiateSignUpResponse,
  LoginPayload,
  LoginResponse,
  SendOtpPayload,
  SendOtpResponse,
  VerifyOtpCodePayload,
  VerifyOtpCodeResponse,
} from '@/types/api/auth';
import { ActionResult, toActionError } from '@/shared/api/actionResult';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';

const COOKIE_SECURE = process.env.COOKIE_SECURE === 'true';

export async function logoutAction() {
  try {
    await apiServer.post('/api/auth/logout', {}, { requireAuth: true });
  } catch (error) {
    console.error('Logout failed on backend:', error);
  }

  const cookieStore = await cookies();
  cookieStore.delete('accessToken');
  cookieStore.delete('refreshToken');

  redirect('/');
}

export async function initiateSignUpAction(
  payload: InitiateSignUpPayload
): Promise<ActionResult<InitiateSignUpResponse>> {
  try {
    console.log('[initiateSignUpAction] Payload:', payload);
    const data = await apiServer.post<InitiateSignUpResponse>(
      '/api/auth/signup/initiate',
      payload,
      {
        requireAuth: false,
      }
    );
    return { success: true, data };
  } catch (error) {
    console.error('[initiateSignUpAction] Error in initiateSignUpAction:', error);
    return toActionError(error);
  }
}

export async function verifyOtpCodeAction(
  payload: VerifyOtpCodePayload
): Promise<ActionResult<VerifyOtpCodeResponse>> {
  try {
    const data = await apiServer.post<VerifyOtpCodeResponse>('/api/auth/verify-code', payload, {
      requireAuth: false,
    });

    if (data.accessToken && data.refreshToken) {
      const cookieStore = await cookies();

      cookieStore.set('accessToken', data.accessToken, {
        httpOnly: true,
        secure: COOKIE_SECURE,
        path: '/',
        maxAge: 15 * 60, // 15 minutes
        sameSite: 'lax',
      });

      cookieStore.set('refreshToken', data.refreshToken, {
        httpOnly: true,
        secure: COOKIE_SECURE,
        path: '/',
        maxAge: 7 * 24 * 60 * 60, // 7 days
        sameSite: 'lax',
      });
    } else {
      throw new Error('Login response is missing accessToken or refreshToken');
    }

    return { success: true, data };
  } catch (error) {
    console.error('[verifyOtpCodeAction] Error in verifyOtpCodeAction:', error);
    return toActionError(error);
  }
}

export async function loginWithPasswordAction(
  payload: LoginPayload
): Promise<ActionResult<LoginResponse>> {
  try {
    const data = await apiServer.post<LoginResponse>('/api/auth/login', payload, {
      requireAuth: false,
    });

    if (data.accessToken && data.refreshToken) {
      const cookieStore = await cookies();

      cookieStore.set('accessToken', data.accessToken, {
        httpOnly: true,
        secure: COOKIE_SECURE,
        path: '/',
        maxAge: 15 * 60, // 15 minutes
        sameSite: 'lax',
      });

      cookieStore.set('refreshToken', data.refreshToken, {
        httpOnly: true,
        secure: COOKIE_SECURE,
        path: '/',
        maxAge: 7 * 24 * 60 * 60, // 7 days
        sameSite: 'lax',
      });
    } else {
      throw new Error('Login response is missing accessToken or refreshToken');
    }

    return { success: true, data };
  } catch (error) {
    console.error('[loginWithPasswordAction] Error in loginWithPasswordAction:', error);
    return toActionError(error);
  }
}

export async function sendOtpCodeAction(
  payload: SendOtpPayload
): Promise<ActionResult<SendOtpResponse>> {
  try {
    const data = await apiServer.post<SendOtpResponse>('/api/auth/send-otp', payload, {
      requireAuth: false,
    });
    return { success: true, data };
  } catch (error) {
    console.error('[sendOtpCodeAction] Error in sendOtpCodeAction:', error);
    return toActionError(error);
  }
}

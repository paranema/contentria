'use client';

import { PATHS } from '@/shared/constants/paths';
import { useAuthStore } from '@/store/authStore';
import { LoginPayload, LoginResponse, SendOtpPayload, SendOtpResponse } from '@/types/api/auth';
import {
  InitiateSignUpPayload,
  InitiateSignUpResponse,
  VerifyOtpCodePayload,
  VerifyOtpCodeResponse,
} from '@/types/api/auth';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import {
  initiateSignUpAction,
  loginWithPasswordAction,
  sendOtpCodeAction,
  verifyOtpCodeAction,
} from '@/actions/auth';
import { SerializedError } from '@/shared/api/actionResult';

/**
 * 패스워드로 로그인하는 Mutation
 * 성공 시, 유저 정보를 갱신하고 대시보드로 리다이렉트한다.
 */
export const useLoginWithPasswordMutation = () => {
  const queryClient = useQueryClient();
  const router = useRouter();
  const login = useAuthStore((state) => state.login);

  return useMutation<LoginResponse, SerializedError, LoginPayload>({
    mutationFn: async (payload: LoginPayload) => {
      const result = await loginWithPasswordAction(payload);
      if (!result.success) {
        throw result.error;
      }
      return result.data;
    },
    onSuccess: (data) => {
      login(data.user);
      queryClient.invalidateQueries({ queryKey: ['user', 'me'] });
      router.replace(PATHS.DASHBOARD);
    },
  });
};

/**
 * Initiates the sign-up process and manages the mutation state.
 *
 * This hook wraps the server action for initiating sign-up with TanStack Query,
 * providing automatic loading, error, and success state management.
 *
 * @param onSuccessCallback - Optional callback function to execute after successful sign-up initiation
 * @returns A mutation object with the following properties:
 *   - mutate: Function to trigger the sign-up mutation
 *   - mutateAsync: Async version that returns a promise
 *   - isPending: Loading state
 *   - isError: Error state
 *   - error: Error object if mutation failed
 * @throws {SerializedError} Throws when the server action fails
 */
export const useInitiateSignUpMutation = (onSuccessCallback?: () => void) => {
  return useMutation<InitiateSignUpResponse, SerializedError, InitiateSignUpPayload>({
    mutationFn: async (payload: InitiateSignUpPayload) => {
      const result = await initiateSignUpAction(payload);

      if (!result.success) {
        throw result.error;
      }
      return result.data;
    },
    onSuccess: () => {
      onSuccessCallback?.();
    },
  });
};

/**
 * OTP 코드를 검증하는 뮤테이션
 * 성공 시, 유저 정보를 갱신하고 대시보드로 리다이렉트한다.
 */
export const useVerifyOtpMutation = () => {
  const queryClient = useQueryClient();
  const router = useRouter();
  const login = useAuthStore((state) => state.login);

  return useMutation<VerifyOtpCodeResponse, SerializedError, VerifyOtpCodePayload>({
    mutationFn: async (payload: VerifyOtpCodePayload) => {
      const result = await verifyOtpCodeAction(payload);
      if (!result.success) {
        throw result.error;
      }
      return result.data;
    },
    onSuccess: (data) => {
      console.log('OTP verification successful, logging in user:', data.user);
      login(data.user);
      queryClient.invalidateQueries({ queryKey: ['user', 'me'] });
      router.replace(PATHS.DASHBOARD);
    },
  });
};

/**
 * OTP 코드를 요청(전송)하는 뮤테이션
 * onSuccess 콜백을 통해 특정 UI 로직을 실행할 수 있다.
 */
export const useSendOtpMutation = (onSuccessCallback?: () => void) => {
  return useMutation<SendOtpResponse, SerializedError, SendOtpPayload>({
    mutationFn: async (payload: SendOtpPayload) => {
      const result = await sendOtpCodeAction(payload);
      if (!result.success) {
        throw result.error;
      }
      return result.data;
    },
    onSuccess: () => {
      onSuccessCallback?.();
    },
  });
};

'use client';

import { startGoogleLogin } from '@/lib/oauth';
import { LoginFormData, LoginStep } from '@/components/auth/login/types';
import { PasswordStepFormData, LoginEmailStepFormData } from '@/lib/schemas/authSchemas';
import { createContext, useContext, useEffect, useState } from 'react';
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3';
import {
  useLoginWithPasswordMutation,
  useSendOtpMutation,
  useVerifyOtpMutation,
} from './mutations/useAuthMutations';
import { RECAPTCHA_LOGIN_WITH_PASSWORD_ACTION, RECAPTCHA_SEND_OTP_ACTION } from '@/constants/auth';
import { ERROR_CODES } from '@/constants/errorCodes';
import { ApiError } from '@/shared/api/apiError';

type LoginFlowContextType = ReturnType<typeof useLoginFlowLogic>;

const LoginFlowContext = createContext<LoginFlowContextType | undefined>(undefined);

function useLoginFlowLogic() {
  const [step, setStep] = useState<LoginStep>('email');
  const [formData, setFormData] = useState<LoginFormData>({
    email: '',
    password: '',
    verificationCode: '',
  });
  const [loginAttemptType, setLoginAttemptType] = useState<'password' | 'otp' | null>(null);
  const { executeRecaptcha } = useGoogleReCaptcha();

  const {
    mutate: loginWithPassword,
    isPending: isLoggingIn,
    error: loginError,
    reset: resetLoginWithPassword,
  } = useLoginWithPasswordMutation();
  const {
    mutate: sendOtp,
    isPending: isSendingOtp,
    error: sendOtpError,
    reset: resetSendOtp,
  } = useSendOtpMutation(() => setStep('verify_otp_code'));
  const { mutate: verifyOtp, isPending: isVerifying, error: verifyError } = useVerifyOtpMutation();

  const isLoading = isLoggingIn || isSendingOtp || isVerifying;
  const combinedError = (loginError || sendOtpError || verifyError) as ApiError | null;

  useEffect(() => {
    if (combinedError && combinedError.status === 403 && combinedError.code === 'C0005') {
      setStep('recaptcha_v2_challenge');
      resetLoginWithPassword();
      resetSendOtp();
    }
  }, [combinedError, resetLoginWithPassword, resetSendOtp]);

  useEffect(() => {
    if (
      combinedError &&
      combinedError.code === ERROR_CODES.INVALID_CREDENTIALS &&
      step === 'recaptcha_v2_challenge'
    ) {
      setStep('password');
    }
  }, [combinedError, step]);

  function updateFormData(field: keyof LoginFormData, value: string) {
    setFormData((prev) => ({ ...prev, [field]: value }));
  }

  function goToPreviousStep() {
    if (step === 'password' || step === 'recaptcha_v2_challenge') {
      setStep('email');
    } else if (step === 'verify_otp_code') {
      setStep('password');
    }
  }

  function submitEmailStep(data: LoginEmailStepFormData) {
    console.log('Email submitted:', data.email);
    updateFormData('email', data.email);
    setStep('password');
  }

  async function submitPasswordLogin(data: PasswordStepFormData) {
    setLoginAttemptType('password');
    if (!executeRecaptcha) {
      return;
    }

    try {
      const recaptchaV3Token = await executeRecaptcha(RECAPTCHA_LOGIN_WITH_PASSWORD_ACTION);
      loginWithPassword({
        email: formData.email,
        password: data.password,
        recaptchaV3Token,
      });
    } catch (e) {
      console.error('reCAPTCHA execution error:', e);
    }
  }

  async function requestOtpLogin() {
    setLoginAttemptType('otp');
    if (!executeRecaptcha) {
      return;
    }

    try {
      const recaptchaV3Token = await executeRecaptcha(RECAPTCHA_SEND_OTP_ACTION);
      sendOtp({
        email: formData.email,
        recaptchaV3Token,
      });
    } catch (e) {
      console.error('reCAPTCHA execution error:', e);
    }
  }

  async function verifyRecaptchaAndProceed(recaptchaV2Token: string) {
    if (loginAttemptType === 'password') {
      loginWithPassword({
        email: formData.email,
        password: formData.password,
        recaptchaV2Token,
      });
    } else if (loginAttemptType === 'otp') {
      sendOtp({
        email: formData.email,
        recaptchaV2Token,
      });
    }
  }

  function updateFormDataAndVerify(field: keyof LoginFormData, value: string) {
    updateFormData(field, value);
    if (field === 'verificationCode' && value.length === 6) {
      verifyOtp({ email: formData.email, verificationCode: value });
    }
  }

  async function resendLoginOtpCode() {
    sendOtp({ email: formData.email });
  }

  return {
    step,
    formData,
    isLoading,
    error: combinedError,
    loginAttemptType,
    goToPreviousStep,
    submitEmailStep,
    submitPasswordLogin,
    requestOtpLogin,
    verifyRecaptchaAndProceed,
    updateFormDataAndVerify,
    resendLoginOtpCode,
    startGoogleLogin,
  };
}

export const LoginFlowProvider = ({ children }: { children: React.ReactNode }) => {
  const flow = useLoginFlowLogic();
  return <LoginFlowContext.Provider value={flow}>{children}</LoginFlowContext.Provider>;
};

export const useLoginFlow = (): LoginFlowContextType => {
  const context = useContext(LoginFlowContext);
  if (context === undefined) {
    throw new Error('useLoginFlow must be used within a LoginFlowProvider');
  }
  return context;
};

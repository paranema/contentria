'use client';

import { SerializedError } from '@/shared/api/actionResult';
import { createContext, ReactNode, useContext, useState } from 'react';
import {
  completeCodeLoginAction,
  loginWithPasswordAction,
  startCodeLoginAction,
} from '../api/auth';
import { isAttemptOver, MEMBER_ERRORS } from '../errors';
import { useGoogleOAuth } from '../oauth';
import { RECAPTCHA_ACTIONS } from '../recaptcha';
import { HumanProof } from '../types';
import { useInvisibleRecaptcha } from './useInvisibleRecaptcha';
import { useSignedIn } from './useSignedIn';

/** 이메일 입력 후 비밀번호 또는 이메일 인증 코드로 이어지는 로그인 단계입니다. (01-rules, 흐름 6) */
export type LoginStep = 'email' | 'password' | 'recaptcha_v2_challenge' | 'verify_code';

/** reCAPTCHA v2 체크박스 통과 후 재시도할 로그인 요청 정보입니다. */
type Attempt = { kind: 'password'; password: string } | { kind: 'code' };

function useLoginFlowLogic() {
  const [step, setStep] = useState<LoginStep>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [attemptId, setAttemptId] = useState<string | null>(null);
  const [pending, setPending] = useState<Attempt | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<SerializedError | null>(null);
  const invisibleToken = useInvisibleRecaptcha();
  const { startGoogleLogin } = useGoogleOAuth();
  const signedIn = useSignedIn();

  /** 최초 시도와 reCAPTCHA v2 체크박스 통과 후 재시도에서 공통으로 로그인 요청을 실행합니다. */
  async function run(attempt: Attempt, proof: HumanProof) {
    setPending(attempt);
    setIsLoading(true);
    setError(null);
    if (attempt.kind === 'password') {
      const result = await loginWithPasswordAction({ email, password: attempt.password, ...proof });
      setIsLoading(false);
      if (result.success) signedIn();
      else failed(result.error);
      return;
    }
    const result = await startCodeLoginAction({ email, ...proof });
    setIsLoading(false);
    if (result.success) {
      setAttemptId(result.data.attemptId);
      setCode('');
      setStep('verify_code');
    } else {
      failed(result.error);
    }
  }

  function failed(reason: SerializedError) {
    if (reason.code === MEMBER_ERRORS.RECAPTCHA_CHALLENGE_REQUIRED) {
      setStep('recaptcha_v2_challenge');
    } else {
      setError(reason);
      setStep('password');
    }
  }

  function submitEmail(value: string) {
    setEmail(value);
    setError(null);
    setStep('password');
  }

  async function submitPassword(password: string) {
    await run(
      { kind: 'password', password },
      { recaptchaV3Token: await invisibleToken(RECAPTCHA_ACTIONS.LOGIN) }
    );
  }

  async function requestCode() {
    await run(
      { kind: 'code' },
      { recaptchaV3Token: await invisibleToken(RECAPTCHA_ACTIONS.LOGIN_CODE) }
    );
  }

  async function submitRecaptchaV2(token: string) {
    if (pending) await run(pending, { recaptchaV2Token: token });
  }

  async function changeCode(value: string) {
    setCode(value);
    setError(null);
    if (value.length !== 6 || !attemptId) return;
    setIsLoading(true);
    const result = await completeCodeLoginAction({ attemptId, code: value });
    setIsLoading(false);
    if (result.success) {
      signedIn();
    } else if (isAttemptOver(result.error)) {
      setError(result.error);
      setStep('password');
    } else {
      setError(result.error);
      setCode('');
    }
  }

  function goToPreviousStep() {
    setError(null);
    if (step === 'verify_code') setStep('password');
    else setStep('email');
  }

  return {
    step,
    email,
    code,
    isLoading,
    error,
    pendingKind: pending?.kind ?? null,
    submitEmail,
    submitPassword,
    requestCode,
    submitRecaptchaV2,
    changeCode,
    resendCode: requestCode,
    goToPreviousStep,
    startGoogleLogin,
  };
}

type LoginFlowContextType = ReturnType<typeof useLoginFlowLogic>;

const LoginFlowContext = createContext<LoginFlowContextType | undefined>(undefined);

export function LoginFlowProvider({ children }: { children: ReactNode }) {
  const flow = useLoginFlowLogic();
  return <LoginFlowContext.Provider value={flow}>{children}</LoginFlowContext.Provider>;
}

export function useLoginFlow(): LoginFlowContextType {
  const context = useContext(LoginFlowContext);
  if (context === undefined) {
    throw new Error('useLoginFlow must be used within a LoginFlowProvider');
  }
  return context;
}

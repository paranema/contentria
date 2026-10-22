'use client';

import { SerializedError } from '@/shared/api/actionResult';
import { createContext, ReactNode, useContext, useState } from 'react';
import { completeSignUpAction, startSignUpAction } from '../api/auth';
import { isAttemptOver, MEMBER_ERRORS } from '../errors';
import { startGoogleLogin } from '../oauth';
import { RECAPTCHA_ACTIONS } from '../recaptcha';
import { HumanProof } from '../types';
import { useInvisibleRecaptcha } from './useInvisibleRecaptcha';
import { useSignedIn } from './useSignedIn';

/**
 * docs/contexts/member/01-rules.md, 흐름 1: email, code, password. The code is checked when the
 * password goes with it, so a wrong code sends the person back to the code step.
 */
export type SignUpStep = 'email' | 'recaptcha_v2_challenge' | 'verify_code' | 'password';

function useSignUpFlowLogic() {
  const [step, setStep] = useState<SignUpStep>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [attemptId, setAttemptId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<SerializedError | null>(null);
  const invisibleToken = useInvisibleRecaptcha();
  const signedIn = useSignedIn();

  async function sendCode(to: string, proof: HumanProof) {
    setIsLoading(true);
    setError(null);
    const result = await startSignUpAction({ email: to, ...proof });
    setIsLoading(false);
    if (result.success) {
      setAttemptId(result.data.attemptId);
      setCode('');
      setStep('verify_code');
    } else if (result.error.code === MEMBER_ERRORS.RECAPTCHA_CHALLENGE_REQUIRED) {
      setStep('recaptcha_v2_challenge');
    } else {
      setError(result.error);
      setStep('email');
    }
  }

  async function submitEmail(value: string) {
    setEmail(value);
    await sendCode(value, { recaptchaV3Token: await invisibleToken(RECAPTCHA_ACTIONS.SIGN_UP) });
  }

  async function submitRecaptchaV2(token: string) {
    await sendCode(email, { recaptchaV2Token: token });
  }

  /** A new code makes the earlier one useless (01-rules, 가입 시도). */
  async function resendCode() {
    await sendCode(email, { recaptchaV3Token: await invisibleToken(RECAPTCHA_ACTIONS.SIGN_UP) });
  }

  function changeCode(value: string) {
    setCode(value);
    setError(null);
    if (value.length === 6) setStep('password');
  }

  async function submitPassword(password: string) {
    if (!attemptId) return;
    setIsLoading(true);
    setError(null);
    const result = await completeSignUpAction({ attemptId, code, password });
    setIsLoading(false);
    if (result.success) {
      signedIn();
    } else if (result.error.code === MEMBER_ERRORS.VERIFICATION_MISMATCH) {
      setError(result.error);
      setCode('');
      setStep('verify_code');
    } else if (isAttemptOver(result.error)) {
      setError(result.error);
      setStep('email');
    } else {
      setError(result.error);
    }
  }

  function goToPreviousStep() {
    setError(null);
    if (step === 'password') setStep('verify_code');
    else setStep('email');
  }

  return {
    step,
    email,
    code,
    isLoading,
    error,
    submitEmail,
    submitRecaptchaV2,
    resendCode,
    changeCode,
    submitPassword,
    goToPreviousStep,
    startGoogleLogin,
  };
}

type SignUpFlowContextType = ReturnType<typeof useSignUpFlowLogic>;

const SignUpFlowContext = createContext<SignUpFlowContextType | undefined>(undefined);

export function SignUpFlowProvider({ children }: { children: ReactNode }) {
  const flow = useSignUpFlowLogic();
  return <SignUpFlowContext.Provider value={flow}>{children}</SignUpFlowContext.Provider>;
}

export function useSignUpFlow(): SignUpFlowContextType {
  const context = useContext(SignUpFlowContext);
  if (context === undefined) {
    throw new Error('useSignUpFlow must be used within a SignUpFlowProvider');
  }
  return context;
}

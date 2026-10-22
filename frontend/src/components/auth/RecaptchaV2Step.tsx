'use client';

import BackButton from '@/shared/components/BackButton';
import { useEffect, useRef, useState } from 'react';
import ReCAPTCHA from 'react-google-recaptcha';
import { Loader2, ShieldCheck } from 'lucide-react';

interface RecaptchaV2StepProps {
  isLoading: boolean;
  error: string | null;
  goToPreviousStep: () => void;
  onVerify: (token: string) => Promise<void> | void; // 비동기 함수도 받을 수 있도록 Promise 추가
  onError: (errorMessage: string) => void;
}

export default function RecaptchaV2Step({
  isLoading,
  error,
  goToPreviousStep,
  onVerify,
  onError,
}: RecaptchaV2StepProps) {
  const recaptchaV2Ref = useRef<ReCAPTCHA>(null);
  const siteKeyV2 = process.env.NEXT_PUBLIC_RECAPTCHA_V2_CHECKBOX_SITE_KEY;

  const [isResetting, setIsResetting] = useState(false);

  useEffect(() => {
    if (error) {
      setIsResetting(true);
      const timer = setTimeout(() => {
        recaptchaV2Ref.current?.reset();
        setIsResetting(false);
      }, 300);
      return () => clearTimeout(timer);
    }
  }, [error]);

  const handleV2TokenSubmit = async (v2Token: string | null) => {
    if (!v2Token) {
      return;
    }
    await onVerify(v2Token);
  };

  const handleOnExpired = () => {
    onError('reCAPTCHA challenge expired. Please refresh or try again.');
  };

  const handleOnError = () => {
    onError('Failed to load reCAPTCHA. Please check your network or try again later.');
  };

  if (!siteKeyV2) {
    return (
      <>
        <BackButton onClick={goToPreviousStep} />
        <p className="mt-4 text-center text-red-500">
          reCAPTCHA v2 is not configured. Please contact support.
        </p>
      </>
    );
  }

  return (
    <>
      <BackButton onClick={goToPreviousStep} disabled={isLoading || isResetting} />

      <div className="mt-4 text-center">
        <ShieldCheck className="mx-auto h-12 w-12 text-gray-400" />
        <h2 className="mt-2 text-lg font-medium text-gray-900">Security Check</h2>
        <p className="mt-2 text-sm text-gray-600">
          Before continuing, we need to be sure you are human.
        </p>
      </div>

      <div className="mt-8 flex flex-col items-center justify-center">
        <div
          className={`flex min-h-[78px] items-center justify-center transition-all duration-300 ease-in-out ${isResetting ? 'scale-95 opacity-0' : 'scale-100 opacity-100'} ${isLoading ? 'pointer-events-none opacity-50' : ''}`}
        >
          <ReCAPTCHA
            ref={recaptchaV2Ref}
            sitekey={siteKeyV2}
            onChange={handleV2TokenSubmit}
            onErrored={handleOnError}
            onExpired={handleOnExpired}
            hl="ko"
          />
        </div>
      </div>

      <div className="relative mt-4 h-6 w-full">
        {/* 로딩 인디케이터 */}
        <div
          className={`absolute inset-0 flex items-center justify-center space-x-2 text-indigo-600 transition-all duration-300 ${isLoading ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-1 opacity-0'} `}
        >
          <Loader2 className="h-4 w-4 animate-spin" />
          <span className="text-sm font-medium">인증 처리 중...</span>
        </div>

        {/* 에러 메시지 */}
        <div
          className={`absolute inset-0 flex items-center justify-center transition-all duration-300 ${error && !isResetting && !isLoading ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-1 opacity-0'} `}
        >
          <p className="text-sm text-red-600">{error}</p>
        </div>
      </div>
    </>
  );
}

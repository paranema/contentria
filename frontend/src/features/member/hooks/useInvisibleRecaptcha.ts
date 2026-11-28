'use client';

import { usePublicConfig } from '@/shared/providers/PublicConfigProvider';
import { useCallback } from 'react';
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3';
import { RecaptchaAction } from '../recaptcha';

/**
 * 지정한 액션에 대한 reCAPTCHA v3 토큰을 발급합니다.
 * 사이트 키가 없거나(검증이 비활성화된 로컬 개발 환경) 스크립트가 아직 로드되지 않았으면 undefined를 반환하며,
 * 토큰이 없을 때 API가 v2 체크박스 인증을 요청하므로 흐름은 그대로 이어집니다.
 */
export function useInvisibleRecaptcha() {
  const { recaptchaV3SiteKey } = usePublicConfig();
  const { executeRecaptcha } = useGoogleReCaptcha();
  return useCallback(
    async (action: RecaptchaAction): Promise<string | undefined> => {
      // 사이트 키가 없으면 RecaptchaWrapper가 Provider를 감싸지 않아 기본 훅 호출 시 예외가 발생합니다.
      if (!recaptchaV3SiteKey || !executeRecaptcha) return undefined;
      try {
        return await executeRecaptcha(action);
      } catch (error) {
        console.error('reCAPTCHA execution error:', error);
        return undefined;
      }
    },
    [recaptchaV3SiteKey, executeRecaptcha]
  );
}

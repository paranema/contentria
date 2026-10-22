'use client';

import { useCallback } from 'react';
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3';
import { RecaptchaAction } from '../recaptcha';

const SITE_KEY = process.env.NEXT_PUBLIC_RECAPTCHA_V3_SITE_KEY;

/**
 * A v3 token for [action], or undefined when there is none to give: no site key (local
 * development, where the API's check is off) or the script not loaded yet. Without a token the
 * API asks for the checkbox, so the flow still goes on.
 */
export function useInvisibleRecaptcha() {
  const { executeRecaptcha } = useGoogleReCaptcha();
  return useCallback(
    async (action: RecaptchaAction): Promise<string | undefined> => {
      // Without a site key RecaptchaWrapper sets up no provider, and the hook's stand-in throws.
      if (!SITE_KEY || !executeRecaptcha) return undefined;
      try {
        return await executeRecaptcha(action);
      } catch (error) {
        console.error('reCAPTCHA execution error:', error);
        return undefined;
      }
    },
    [executeRecaptcha]
  );
}

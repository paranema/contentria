'use client';

import { usePublicConfig } from '@/shared/providers/PublicConfigProvider';
import { useCallback } from 'react';
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3';
import { RecaptchaAction } from '../recaptcha';

/**
 * A v3 token for [action], or undefined when there is none to give: no site key (local
 * development, where the API's check is off) or the script not loaded yet. Without a token the
 * API asks for the checkbox, so the flow still goes on.
 */
export function useInvisibleRecaptcha() {
  const { recaptchaV3SiteKey } = usePublicConfig();
  const { executeRecaptcha } = useGoogleReCaptcha();
  return useCallback(
    async (action: RecaptchaAction): Promise<string | undefined> => {
      // Without a site key RecaptchaWrapper sets up no provider, and the hook's stand-in throws.
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

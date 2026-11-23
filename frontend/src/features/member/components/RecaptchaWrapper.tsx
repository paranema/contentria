'use client';

import { usePublicConfig } from '@/shared/providers/PublicConfigProvider';
import { GoogleReCaptchaProvider } from 'react-google-recaptcha-v3';

export default function RecaptchaWrapper({ children }: { children: React.ReactNode }) {
  const { recaptchaV3SiteKey } = usePublicConfig();

  if (!recaptchaV3SiteKey) {
    return <>{children}</>;
  }

  return (
    <GoogleReCaptchaProvider
      reCaptchaKey={recaptchaV3SiteKey}
      language="ko"
      scriptProps={{ async: true, defer: true }}
    >
      {children}
    </GoogleReCaptchaProvider>
  );
}

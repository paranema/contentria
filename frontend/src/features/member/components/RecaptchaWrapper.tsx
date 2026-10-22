'use client';

import { GoogleReCaptchaProvider } from 'react-google-recaptcha-v3';

export default function RecaptchaWrapper({ children }: { children: React.ReactNode }) {
  const recaptchaSiteKey = process.env.NEXT_PUBLIC_RECAPTCHA_V3_SITE_KEY;

  if (!recaptchaSiteKey) {
    console.warn('reCAPTCHA V3 Site Key is not defined.');
    return <>{children}</>;
  }

  return (
    <GoogleReCaptchaProvider
      reCaptchaKey={recaptchaSiteKey}
      language="ko"
      scriptProps={{ async: true, defer: true }}
    >
      {children}
    </GoogleReCaptchaProvider>
  );
}

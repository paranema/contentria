import RecaptchaWrapper from '@/features/member/components/RecaptchaWrapper';

export const metadata = {
  title: 'Auth',
  description: 'Authentication related pages',
};

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <RecaptchaWrapper>
      <main className="flex min-h-[100dvh] flex-col bg-gray-50">
        <div className="flex flex-1 flex-col items-center justify-center px-4 sm:px-6 lg:px-8">
          <div className="w-full max-w-md sm:-mt-20">{children}</div>
        </div>

        <div className="space-y-1.5 px-4 py-6 text-center text-sm text-gray-500">
          <p>
            By continuing, you agree to our{' '}
            <a href="/policy?tab=privacy" className="text-indigo-600 hover:text-indigo-500">
              Privacy Policy
            </a>{' '}
            and{' '}
            <a href="/policy?tab=terms" className="text-indigo-600 hover:text-indigo-500">
              Terms of Service
            </a>
          </p>
          {/* reCAPTCHA 배지를 숨기는 대신 Google 정책이 요구하는 보호 문구를 표기합니다. (https://developers.google.com/recaptcha/docs/faq) */}
          <p className="text-xs text-gray-400">
            This site is protected by reCAPTCHA and the Google{' '}
            <a
              href="https://policies.google.com/privacy"
              target="_blank"
              rel="noreferrer"
              className="underline hover:text-gray-600"
            >
              Privacy Policy
            </a>{' '}
            and{' '}
            <a
              href="https://policies.google.com/terms"
              target="_blank"
              rel="noreferrer"
              className="underline hover:text-gray-600"
            >
              Terms of Service
            </a>{' '}
            apply.
          </p>
        </div>
      </main>
    </RecaptchaWrapper>
  );
}

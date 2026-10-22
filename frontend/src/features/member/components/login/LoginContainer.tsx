'use client';

import { memberErrorMessage } from '../../errors';
import { LoginFlowProvider, useLoginFlow } from '../../hooks/useLoginFlow';
import AuthFormCard from '../AuthFormCard';
import VerificationStep from '../VerificationStep';
import RecaptchaV2Step from '../RecaptchaV2Step';
import PasswordStep from './PasswordStep';
import EmailStep from './EmailStep';

const LoginFlow = ({ notice }: { notice: string | null }) => {
  const loginFlow = useLoginFlow();
  const error = memberErrorMessage(loginFlow.error);

  const getTitle = () => (loginFlow.step === 'verify_code' ? 'Verify your email' : 'Sign in');

  const renderCurrentStep = () => {
    switch (loginFlow.step) {
      case 'email':
        return <EmailStep notice={notice} />;
      case 'password':
        return <PasswordStep />;
      case 'recaptcha_v2_challenge':
        return (
          <RecaptchaV2Step
            isLoading={loginFlow.isLoading}
            error={error}
            goToPreviousStep={loginFlow.goToPreviousStep}
            onVerify={loginFlow.submitRecaptchaV2}
            onError={(errorMessage) => console.error('reCAPTCHA error:', errorMessage)}
          />
        );
      case 'verify_code':
        return (
          <VerificationStep
            email={loginFlow.email}
            verificationCode={loginFlow.code}
            isLoading={loginFlow.isLoading}
            error={error}
            onCodeChange={loginFlow.changeCode}
            onResendCode={loginFlow.resendCode}
          />
        );
      default:
        return null;
    }
  };

  return <AuthFormCard title={getTitle()}>{renderCurrentStep()}</AuthFormCard>;
};

/** [notice]: what to say before anything is tried, such as a failed Google sign-in or an expired session. */
export default function LoginContainer({ notice = null }: { notice?: string | null }) {
  return (
    <LoginFlowProvider>
      <LoginFlow notice={notice} />
    </LoginFlowProvider>
  );
}

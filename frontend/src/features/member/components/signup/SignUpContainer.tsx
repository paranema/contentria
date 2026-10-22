'use client';

import { memberErrorMessage } from '../../errors';
import { SignUpFlowProvider, useSignUpFlow } from '../../hooks/useSignUpFlow';
import AuthFormCard from '../AuthFormCard';
import VerificationStep from '../VerificationStep';
import RecaptchaV2Step from '../RecaptchaV2Step';
import PasswordStep from './PasswordStep';
import EmailStep from './EmailStep';

function SignUpFlow() {
  const signUpFlow = useSignUpFlow();
  const error = memberErrorMessage(signUpFlow.error);

  function getTitle() {
    return signUpFlow.step === 'verify_code' ? 'Verify your email' : 'Sign up';
  }

  function renderCurrentStep() {
    switch (signUpFlow.step) {
      case 'email':
        return <EmailStep />;
      case 'password':
        return <PasswordStep />;
      case 'recaptcha_v2_challenge':
        return (
          <RecaptchaV2Step
            isLoading={signUpFlow.isLoading}
            error={error}
            goToPreviousStep={signUpFlow.goToPreviousStep}
            onVerify={signUpFlow.submitRecaptchaV2}
            onError={(errorMessage) => console.error('reCAPTCHA error:', errorMessage)}
          />
        );
      case 'verify_code':
        return (
          <VerificationStep
            email={signUpFlow.email}
            verificationCode={signUpFlow.code}
            isLoading={signUpFlow.isLoading}
            error={error}
            onCodeChange={signUpFlow.changeCode}
            onResendCode={signUpFlow.resendCode}
          />
        );
      default:
        return null;
    }
  }

  return <AuthFormCard title={getTitle()}>{renderCurrentStep()}</AuthFormCard>;
}

export default function SignUpContainer() {
  return (
    <SignUpFlowProvider>
      <SignUpFlow />
    </SignUpFlowProvider>
  );
}

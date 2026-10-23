'use client';

import { SerializedError } from '@/shared/api/actionResult';
import { PATHS } from '@/shared/constants/paths';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2, MailCheck } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { requestPasswordResetLinkAction } from '../../api/account';
import { MEMBER_ERRORS, memberErrorMessage } from '../../errors';
import { useInvisibleRecaptcha } from '../../hooks/useInvisibleRecaptcha';
import { RECAPTCHA_ACTIONS } from '../../recaptcha';
import { EmailStepFormData, emailStepSchema } from '../../schemas';
import { HumanProof } from '../../types';
import AuthFormCard from '../AuthFormCard';
import RecaptchaV2Step from '../RecaptchaV2Step';

type Step = 'email' | 'recaptcha_v2_challenge' | 'sent';

/**
 * A forgotten password (01-rules 흐름 3): a link to the address. The API answers alike whether or
 * not an account uses it, so the screen does too.
 */
export default function ForgotPasswordForm() {
  const [step, setStep] = useState<Step>('email');
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<SerializedError | null>(null);
  const invisibleToken = useInvisibleRecaptcha();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<EmailStepFormData>({
    resolver: zodResolver(emailStepSchema),
    defaultValues: { email: '' },
  });

  async function send(address: string, proof: HumanProof) {
    setIsLoading(true);
    setError(null);
    const result = await requestPasswordResetLinkAction({ email: address, ...proof });
    setIsLoading(false);
    if (result.success) {
      setStep('sent');
    } else if (result.error.code === MEMBER_ERRORS.RECAPTCHA_CHALLENGE_REQUIRED) {
      setStep('recaptcha_v2_challenge');
    } else {
      setError(result.error);
      setStep('email');
    }
  }

  const onSubmit = async (data: EmailStepFormData) => {
    setEmail(data.email);
    await send(data.email, {
      recaptchaV3Token: await invisibleToken(RECAPTCHA_ACTIONS.PASSWORD_RESET),
    });
  };

  if (step === 'sent') {
    return (
      <AuthFormCard title="Check your email">
        <div className="text-center">
          <MailCheck className="mx-auto h-12 w-12 text-indigo-500" />
          <p className="mt-4 text-sm text-gray-600">
            If an account uses <span className="font-medium text-gray-900">{email}</span>, we sent
            it a link to set a new password.
          </p>
          <Link
            href={PATHS.LOGIN}
            className="mt-6 inline-block text-sm font-medium text-indigo-600 hover:underline"
          >
            Back to sign in
          </Link>
        </div>
      </AuthFormCard>
    );
  }

  if (step === 'recaptcha_v2_challenge') {
    return (
      <AuthFormCard title="Reset your password">
        <RecaptchaV2Step
          isLoading={isLoading}
          error={memberErrorMessage(error)}
          goToPreviousStep={() => setStep('email')}
          onVerify={(token) => send(email, { recaptchaV2Token: token })}
          onError={(message) => console.error('reCAPTCHA error:', message)}
        />
      </AuthFormCard>
    );
  }

  const displayError = errors.email?.message || memberErrorMessage(error);

  return (
    <AuthFormCard title="Reset your password">
      <form className="flex flex-col" onSubmit={handleSubmit(onSubmit)}>
        <p className="text-sm text-gray-600">
          Enter your account&apos;s email address and we will send you a link to set a new password.
        </p>
        <div className="mt-5 space-y-2">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            disabled={isLoading}
            {...register('email')}
          />
        </div>
        {displayError && (
          <p className="mt-1.5 text-sm font-medium text-destructive">{displayError}</p>
        )}
        <Button
          type="submit"
          className="mt-4 w-full bg-indigo-600 hover:bg-indigo-500"
          disabled={isLoading}
        >
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Send link
        </Button>
        <Link
          href={PATHS.LOGIN}
          className="mt-4 text-center text-sm font-medium text-indigo-600 hover:underline"
        >
          Back to sign in
        </Link>
      </form>
    </AuthFormCard>
  );
}

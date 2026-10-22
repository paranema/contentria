'use client';

import BackButton from '@/shared/components/BackButton';
import { memberErrorMessage } from '../../errors';
import { NewPasswordFormData, newPasswordSchema } from '../../schemas';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, Info, Loader2 } from 'lucide-react';
import { useMemo, useState } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import PasswordPolicyTooltip from './PasswordPolicyTooltip';
import { useSignUpFlow } from '../../hooks/useSignUpFlow';
import { Label } from '@/shared/ui/label';
import { Input } from '@/shared/ui/input';
import { Button } from '@/shared/ui/button';

interface PolicyItem {
  id: string;
  text: string;
  isValid: (password: string) => boolean;
}

export default function PasswordStep() {
  const { email, goToPreviousStep, error, isLoading, submitPassword } = useSignUpFlow();

  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
    watch,
  } = useForm<NewPasswordFormData>({
    resolver: zodResolver(newPasswordSchema),
    defaultValues: { password: '' },
  });

  const currentPassword = watch('password', '');
  const [isPolicyVisible, setIsPolicyVisible] = useState(false);

  const passwordPolicies = useMemo(
    (): PolicyItem[] => [
      { id: 'min', text: '8자 이상', isValid: (pw) => pw.length >= 8 },
      { id: 'max', text: '72자 이하', isValid: (pw) => pw.length > 0 && pw.length <= 72 },
    ],
    []
  );

  const processPasswordSubmit: SubmitHandler<NewPasswordFormData> = (data) =>
    submitPassword(data.password);

  const displayError = errors.password?.message || memberErrorMessage(error);

  return (
    <>
      <BackButton onClick={goToPreviousStep} disabled={isLoading} />

      <form className="mt-8 flex flex-col" onSubmit={handleSubmit(processPasswordSubmit)}>
        <div className="space-y-2">
          <Label htmlFor="email-display-password">Email</Label>
          <Input
            id="email-display-password"
            type="email"
            disabled
            value={email}
            className="bg-muted"
            data-bwignore="true"
            data-1p-ignore="true"
            data-lpignore="true"
          />
        </div>

        <div className="mt-5 space-y-2">
          <div className="flex items-center space-x-1.5">
            <Label htmlFor="password">Password</Label>
            <div className="relative flex items-center">
              <button
                type="button"
                onMouseEnter={() => setIsPolicyVisible(true)}
                onMouseLeave={() => setIsPolicyVisible(false)}
                onFocus={() => setIsPolicyVisible(true)}
                onBlur={() => setIsPolicyVisible(false)}
                className="rounded-full text-muted-foreground transition-colors hover:text-foreground focus:outline-none"
                aria-label="Show password policy"
                aria-expanded={isPolicyVisible}
              >
                <Info size={16} />
              </button>
              <PasswordPolicyTooltip
                policies={passwordPolicies}
                currentPasswordValue={currentPassword || ''}
                isVisible={isPolicyVisible}
              />
            </div>
          </div>

          <div className="relative">
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              placeholder="Enter your password"
              autoComplete="new-password"
              disabled={isLoading}
              {...register('password')}
              className={`pr-10 ${displayError ? 'border-destructive focus-visible:ring-destructive' : ''}`}
              data-bwignore="true"
              data-1p-ignore="true"
              data-lpignore="true"
            />
            <button
              type="button"
              onClick={() => setShowPassword((prev) => !prev)}
              disabled={isLoading}
              className="absolute right-0 top-0 flex h-full w-10 items-center justify-center text-muted-foreground hover:text-foreground focus:outline-none disabled:opacity-50"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
        </div>

        <div className="relative mt-1.5 h-5 w-full">
          <div
            className={`absolute inset-0 transition-all duration-300 ${displayError ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-1 opacity-0'}`}
          >
            <p className="text-sm font-medium text-destructive">{displayError}</p>
          </div>
        </div>

        <Button
          type="submit"
          className="mt-4 w-full bg-indigo-600 hover:bg-indigo-500"
          disabled={isLoading}
        >
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          {isLoading ? 'Creating account...' : 'Create account'}
        </Button>
      </form>
    </>
  );
}

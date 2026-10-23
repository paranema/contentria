'use client';

import BackButton from '@/shared/components/BackButton';
import { PATHS } from '@/shared/constants/paths';
import Link from 'next/link';
import { memberErrorMessage } from '../../errors';
import { PasswordFormData, passwordSchema } from '../../schemas';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, Loader2, Mail } from 'lucide-react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { useLoginFlow } from '../../hooks/useLoginFlow';
import { Label } from '@/shared/ui/label';
import { Input } from '@/shared/ui/input';
import { Button } from '@/shared/ui/button';
import { Separator } from '@/shared/ui/separator';
import { useState } from 'react';

export default function PasswordStep() {
  const { email, isLoading, error, pendingKind, goToPreviousStep, submitPassword, requestCode } =
    useLoginFlow();

  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PasswordFormData>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { password: '' },
  });

  const processPasswordSubmit: SubmitHandler<PasswordFormData> = (data) =>
    submitPassword(data.password);

  const displayError = errors.password?.message || memberErrorMessage(error);

  return (
    <>
      <BackButton onClick={goToPreviousStep} />

      <form className="mt-8 flex flex-col" onSubmit={handleSubmit(processPasswordSubmit)}>
        <div className="space-y-2">
          <Label htmlFor="email-display">Email</Label>
          <Input
            id="email-display"
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
          <div className="flex items-center justify-between">
            <Label htmlFor="password">Password</Label>
            <Link
              href={PATHS.FORGOT_PASSWORD}
              className="text-sm font-medium text-indigo-600 hover:text-indigo-500 hover:underline"
            >
              Forgot your password?
            </Link>
          </div>

          <div className="relative">
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              placeholder="Enter your password"
              autoComplete="current-password"
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
          {isLoading && pendingKind === 'password' && (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          )}
          {isLoading && pendingKind === 'password' ? 'Signing in...' : 'Sign in'}
        </Button>
      </form>

      <div className="mt-6">
        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <Separator className="w-full" />
          </div>
          <div className="relative flex justify-center text-xs uppercase">
            <span className="bg-background px-2 text-muted-foreground">Or</span>
          </div>
        </div>

        <div className="mt-6">
          <Button
            type="button"
            variant="outline"
            className="w-full bg-white text-black hover:bg-gray-100"
            onClick={requestCode}
            disabled={isLoading}
          >
            {isLoading && pendingKind === 'code' ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Mail className="mr-2 h-4 w-4" />
            )}
            {isLoading && pendingKind === 'code' ? 'Sending...' : 'Email sign-in code'}
          </Button>
        </div>
      </div>
    </>
  );
}

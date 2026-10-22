'use client';

import { EmailStepFormData, emailStepSchema } from '../../schemas';
import { zodResolver } from '@hookform/resolvers/zod';
import { SubmitHandler, useForm } from 'react-hook-form';
import GoogleLoginButton from '../GoogleLoginButton';
import Link from 'next/link';
import { PATHS } from '@/shared/constants/paths';
import { useLoginFlow } from '../../hooks/useLoginFlow';
import { Label } from '@/shared/ui/label';
import { Input } from '@/shared/ui/input';
import { Button } from '@/shared/ui/button';
import { Loader2 } from 'lucide-react';
import { Separator } from '@/shared/ui/separator';

export default function EmailStep({ notice }: { notice: string | null }) {
  const { email, isLoading, submitEmail, startGoogleLogin } = useLoginFlow();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<EmailStepFormData>({
    resolver: zodResolver(emailStepSchema),
    defaultValues: { email },
  });

  const onSubmit: SubmitHandler<EmailStepFormData> = (data) => submitEmail(data.email);

  const displayError = errors.email?.message || notice;

  return (
    <>
      <form className="mt-8 flex flex-col" onSubmit={handleSubmit(onSubmit)}>
        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            placeholder="Your email address"
            autoComplete="email"
            disabled={isLoading}
            {...register('email')}
            className={errors.email ? 'border-destructive focus-visible:ring-destructive' : ''}
            data-bwignore="true"
            data-1p-ignore="true"
            data-lpignore="true"
          />
          {errors.email && (
            <p className="text-sm font-medium text-destructive">{errors.email.message}</p>
          )}
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
          {isLoading ? 'Processing...' : 'Continue'}
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
          <GoogleLoginButton onClick={startGoogleLogin} />
        </div>
      </div>

      <div className="mt-4 text-center">
        <p className="text-sm text-muted-foreground">
          Don't have an account?{' '}
          <Link
            href={PATHS.SIGNUP}
            className="font-medium text-indigo-600 hover:text-indigo-500 hover:underline"
          >
            Sign up
          </Link>
        </p>
      </div>
    </>
  );
}

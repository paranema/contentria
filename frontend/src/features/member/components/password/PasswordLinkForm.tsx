'use client';

import { SerializedError } from '@/shared/api/actionResult';
import { PATHS } from '@/shared/constants/paths';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { resetPasswordAction } from '../../api/account';
import { memberErrorMessage } from '../../errors';
import { useSignedIn } from '../../hooks/useSignedIn';
import { NewPasswordFormData, newPasswordSchema } from '../../schemas';
import AuthFormCard from '../AuthFormCard';

/**
 * The page a mailed password link opens, for a reset and for a social-only account's first
 * password alike (MEMBER_PASSWORD_LINK_URL). The token comes in the fragment, which never reaches
 * a server log, and is dropped from the address bar once read. Setting the password signs in.
 */
export default function PasswordLinkForm() {
  // undefined: not read yet (the fragment exists only in the browser); null: no token.
  const [token, setToken] = useState<string | null | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<SerializedError | null>(null);
  const signedIn = useSignedIn();

  useEffect(() => {
    const fromHash = new URLSearchParams(window.location.hash.slice(1)).get('token');
    if (fromHash) window.history.replaceState(null, '', window.location.pathname);
    setToken(fromHash);
  }, []);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<NewPasswordFormData>({
    resolver: zodResolver(newPasswordSchema),
    defaultValues: { password: '' },
  });

  const onSubmit = async (data: NewPasswordFormData) => {
    if (!token) return;
    setIsLoading(true);
    setError(null);
    const result = await resetPasswordAction(token, data.password);
    setIsLoading(false);
    if (result.success) signedIn();
    else setError(result.error);
  };

  if (token === undefined) return null;

  if (token === null) {
    return (
      <AuthFormCard title="Set a new password">
        <p className="text-center text-sm text-gray-600">
          This page opens from the link in a password email.
        </p>
        <NewLinkLink />
      </AuthFormCard>
    );
  }

  const displayError = errors.password?.message || memberErrorMessage(error);

  return (
    <AuthFormCard title="Set a new password">
      <form className="flex flex-col" onSubmit={handleSubmit(onSubmit)}>
        <div className="space-y-2">
          <Label htmlFor="password">New password</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            placeholder="At least 8 characters"
            disabled={isLoading}
            {...register('password')}
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
          Set password and sign in
        </Button>
        {error && <NewLinkLink />}
      </form>
    </AuthFormCard>
  );
}

function NewLinkLink() {
  return (
    <Link
      href={PATHS.FORGOT_PASSWORD}
      className="mt-4 block text-center text-sm font-medium text-indigo-600 hover:underline"
    >
      Get a new link
    </Link>
  );
}

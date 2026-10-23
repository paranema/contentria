'use client';

import { Button } from '@/shared/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/shared/ui/card';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useState, useTransition } from 'react';
import { useForm } from 'react-hook-form';
import { changePasswordAction, requestPasswordSetupLinkAction } from '../../api/account';
import { accountErrorMessage } from '../../errors';
import { PasswordChangeFormValues, passwordChangeSchema } from '../../schemas';

/**
 * A new password for an account that has one; an account that signs in only with Google first
 * gets a link mailed to it (01-rules 흐름 3).
 */
export default function PasswordCard({
  hasPassword,
  email,
}: {
  hasPassword: boolean;
  email: string;
}) {
  return hasPassword ? <PasswordChangeForm /> : <PasswordSetup email={email} />;
}

function PasswordChangeForm() {
  const [isPending, startTransition] = useTransition();
  const [failure, setFailure] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
    reset,
  } = useForm<PasswordChangeFormValues>({
    resolver: zodResolver(passwordChangeSchema),
    defaultValues: { currentPassword: '', newPassword: '' },
  });

  const onSubmit = (data: PasswordChangeFormValues) => {
    startTransition(async () => {
      setFailure(null);
      setDone(false);
      const result = await changePasswordAction(data.currentPassword, data.newPassword);
      if (!result.success) {
        setFailure(accountErrorMessage(result.error));
        return;
      }
      reset();
      setDone(true);
    });
  };

  return (
    <Card className="w-full max-w-2xl">
      <CardHeader>
        <CardTitle>비밀번호</CardTitle>
        <CardDescription>바꾸면 이 브라우저를 뺀 다른 곳의 로그인은 모두 끝납니다.</CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit(onSubmit)}>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="currentPassword">현재 비밀번호</Label>
            <Input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              disabled={isPending}
              {...register('currentPassword')}
            />
            {errors.currentPassword && (
              <p className="text-sm font-medium text-red-500">{errors.currentPassword.message}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="newPassword">새 비밀번호</Label>
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              placeholder="8자 이상"
              disabled={isPending}
              {...register('newPassword')}
            />
            {errors.newPassword && (
              <p className="text-sm font-medium text-red-500">{errors.newPassword.message}</p>
            )}
          </div>
          {failure && <p className="text-sm font-medium text-red-500">{failure}</p>}
          {done && <p className="text-sm text-green-600">비밀번호를 바꿨습니다.</p>}
        </CardContent>
        <CardFooter className="flex justify-end border-t bg-gray-50/50 px-6 py-4">
          <Button
            type="submit"
            disabled={!isDirty || isPending}
            className="min-w-[100px] bg-indigo-600 text-white hover:bg-indigo-700"
          >
            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            비밀번호 바꾸기
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}

function PasswordSetup({ email }: { email: string }) {
  const [isPending, startTransition] = useTransition();
  const [failure, setFailure] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const requestLink = () => {
    startTransition(async () => {
      setFailure(null);
      const result = await requestPasswordSetupLinkAction();
      if (!result.success) {
        setFailure(accountErrorMessage(result.error));
        return;
      }
      setSent(true);
    });
  };

  return (
    <Card className="w-full max-w-2xl">
      <CardHeader>
        <CardTitle>비밀번호</CardTitle>
        <CardDescription>
          지금은 Google로만 로그인합니다. 비밀번호를 만들면 이메일로도 로그인할 수 있습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {sent ? (
          <p className="text-sm text-green-600">
            {email}(으)로 비밀번호 설정 링크를 보냈습니다. 메일의 링크를 열어 비밀번호를 정해
            주세요.
          </p>
        ) : (
          <Button variant="outline" onClick={requestLink} disabled={isPending}>
            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            설정 링크 받기
          </Button>
        )}
        {failure && <p className="text-sm font-medium text-red-500">{failure}</p>}
      </CardContent>
    </Card>
  );
}

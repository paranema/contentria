'use client';

import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState, useTransition } from 'react';
import { useForm } from 'react-hook-form';
import { completeEmailChangeAction, startEmailChangeAction } from '../../api/account';
import { isAttemptOver, accountErrorMessage } from '../../errors';
import { memberKeys } from '../../hooks/keys';
import { EmailChangeFormValues, emailChangeSchema } from '../../schemas';

const CODE_LENGTH = 6;

/**
 * 01-rules 흐름 4: codes go to both the current address and the new one, and both must come back.
 * An attempt that is over (locked or expired) starts again from the new address.
 */
export default function EmailChangeCard({ email }: { email: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [isPending, startTransition] = useTransition();
  const [failure, setFailure] = useState<string | null>(null);
  const [attempt, setAttempt] = useState<{ id: string; newEmail: string } | null>(null);
  const [currentCode, setCurrentCode] = useState('');
  const [newCode, setNewCode] = useState('');
  const [done, setDone] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<EmailChangeFormValues>({
    resolver: zodResolver(emailChangeSchema),
    defaultValues: { newEmail: '' },
  });

  const start = (data: EmailChangeFormValues) => {
    startTransition(async () => {
      setFailure(null);
      setDone(false);
      const result = await startEmailChangeAction(data.newEmail);
      if (!result.success) {
        setFailure(accountErrorMessage(result.error));
        return;
      }
      setAttempt({ id: result.data.attemptId, newEmail: data.newEmail });
      setCurrentCode('');
      setNewCode('');
    });
  };

  const complete = () => {
    if (!attempt) return;
    startTransition(async () => {
      setFailure(null);
      const result = await completeEmailChangeAction(attempt.id, currentCode, newCode);
      if (!result.success) {
        setFailure(accountErrorMessage(result.error));
        if (isAttemptOver(result.error)) setAttempt(null);
        return;
      }
      setAttempt(null);
      reset();
      setDone(true);
      queryClient.invalidateQueries({ queryKey: memberKeys.profile() });
      router.refresh();
    });
  };

  const cancel = () => {
    setAttempt(null);
    setFailure(null);
  };

  const codesReady = currentCode.length === CODE_LENGTH && newCode.length === CODE_LENGTH;

  return (
    <Card className="w-full max-w-2xl">
      <CardHeader>
        <CardTitle>이메일 주소</CardTitle>
        <CardDescription>
          지금 주소는 {email}입니다. 바꾸려면 지금 주소와 새 주소로 간 확인 코드를 모두 넣어야
          합니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {attempt ? (
          <>
            <div className="space-y-2">
              <Label htmlFor="currentAddressCode">{email}(으)로 보낸 코드</Label>
              <Input
                id="currentAddressCode"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={CODE_LENGTH}
                value={currentCode}
                onChange={(event) => setCurrentCode(event.target.value.replace(/\D/g, ''))}
                disabled={isPending}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="newAddressCode">{attempt.newEmail}(으)로 보낸 코드</Label>
              <Input
                id="newAddressCode"
                inputMode="numeric"
                maxLength={CODE_LENGTH}
                value={newCode}
                onChange={(event) => setNewCode(event.target.value.replace(/\D/g, ''))}
                disabled={isPending}
              />
            </div>
            {failure && <p className="text-sm font-medium text-red-500">{failure}</p>}
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={cancel} disabled={isPending}>
                취소
              </Button>
              <Button
                onClick={complete}
                disabled={!codesReady || isPending}
                className="bg-indigo-600 text-white hover:bg-indigo-700"
              >
                {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                이메일 바꾸기
              </Button>
            </div>
          </>
        ) : (
          <form onSubmit={handleSubmit(start)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="newEmail">새 이메일 주소</Label>
              <Input id="newEmail" type="email" disabled={isPending} {...register('newEmail')} />
              {errors.newEmail && (
                <p className="text-sm font-medium text-red-500">{errors.newEmail.message}</p>
              )}
            </div>
            {failure && <p className="text-sm font-medium text-red-500">{failure}</p>}
            {done && <p className="text-sm text-green-600">이메일 주소를 바꿨습니다.</p>}
            <div className="flex justify-end">
              <Button type="submit" variant="outline" disabled={isPending}>
                {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                확인 코드 받기
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
}

'use client';

import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { PATHS } from '@/shared/constants/paths';
import { useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState, useTransition } from 'react';
import { completeWithdrawalAction, startWithdrawalAction } from '../../api/account';
import { isAttemptOver, accountErrorMessage } from '../../errors';
import { memberKeys } from '../../hooks/keys';

const CODE_LENGTH = 6;

/** A code mailed to the account's address confirms the withdrawal; then the browser goes home. */
export default function WithdrawalCard({ email }: { email: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [isPending, startTransition] = useTransition();
  const [failure, setFailure] = useState<string | null>(null);
  const [attemptId, setAttemptId] = useState<string | null>(null);
  const [code, setCode] = useState('');

  const start = () => {
    if (!window.confirm('탈퇴하면 되돌릴 수 없습니다. 확인 코드를 받을까요?')) return;
    startTransition(async () => {
      setFailure(null);
      const result = await startWithdrawalAction();
      if (!result.success) {
        setFailure(accountErrorMessage(result.error));
        return;
      }
      setAttemptId(result.data.attemptId);
      setCode('');
    });
  };

  const complete = () => {
    if (!attemptId) return;
    startTransition(async () => {
      setFailure(null);
      const result = await completeWithdrawalAction(attemptId, code);
      if (!result.success) {
        setFailure(accountErrorMessage(result.error));
        if (isAttemptOver(result.error)) setAttemptId(null);
        return;
      }
      queryClient.setQueryData(memberKeys.profile(), null);
      router.replace(PATHS.HOME);
    });
  };

  return (
    <Card className="w-full max-w-2xl border-red-200">
      <CardHeader>
        <CardTitle className="text-red-600">탈퇴</CardTitle>
        <CardDescription>계정을 없애고 모든 기기에서 로그아웃합니다.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {attemptId ? (
          <>
            <div className="space-y-2">
              <Label htmlFor="withdrawalCode">{email}(으)로 보낸 코드</Label>
              <Input
                id="withdrawalCode"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={CODE_LENGTH}
                value={code}
                onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
                disabled={isPending}
              />
            </div>
            {failure && <p className="text-sm font-medium text-red-500">{failure}</p>}
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setAttemptId(null)} disabled={isPending}>
                취소
              </Button>
              <Button
                variant="destructive"
                onClick={complete}
                disabled={code.length !== CODE_LENGTH || isPending}
              >
                {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                탈퇴하기
              </Button>
            </div>
          </>
        ) : (
          <>
            {failure && <p className="text-sm font-medium text-red-500">{failure}</p>}
            <Button variant="destructive" onClick={start} disabled={isPending}>
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              탈퇴하기
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  );
}

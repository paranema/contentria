'use client';

import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState, useTransition } from 'react';
import { unlinkProviderAction } from '../../api/account';
import { accountErrorMessage } from '../../errors';
import { useGoogleOAuth } from '../../oauth';

/**
 * 계정의 로그인 수단을 관리합니다. (01-rules 흐름 3)
 * Google 연동은 브라우저 이동을 거쳐 이 화면으로 돌아오며, 비밀번호는 제거하지 않고 소셜 연동만 해제할 수 있습니다.
 */
export default function LoginMethodsCard({ providers }: { providers: string[] }) {
  const router = useRouter();
  const { startGoogleLink } = useGoogleOAuth();
  const [isPending, startTransition] = useTransition();
  const [failure, setFailure] = useState<string | null>(null);
  const hasPassword = providers.includes('EMAIL');
  const hasGoogle = providers.includes('GOOGLE');

  const unlinkGoogle = () => {
    if (!window.confirm('Google 연동을 해제할까요? 다른 기기의 로그인은 모두 끝납니다.')) return;
    startTransition(async () => {
      setFailure(null);
      const result = await unlinkProviderAction('GOOGLE');
      if (!result.success) {
        setFailure(accountErrorMessage(result.error));
        return;
      }
      // 이전 연동 시 주소창에 남아 있던 `?linked=true` 쿼리를 제거합니다.
      router.replace(window.location.pathname);
    });
  };

  return (
    <Card className="w-full max-w-2xl">
      <CardHeader>
        <CardTitle>로그인 방법</CardTitle>
        <CardDescription>이 계정에 로그인할 수 있는 방법입니다.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="font-medium text-gray-900">이메일과 비밀번호</p>
            <p className="text-sm text-muted-foreground">
              {hasPassword ? '비밀번호가 설정되어 있습니다.' : '아직 비밀번호가 없습니다.'}
            </p>
          </div>
        </div>

        <div className="flex items-center justify-between">
          <div>
            <p className="font-medium text-gray-900">Google</p>
            <p className="text-sm text-muted-foreground">
              {hasGoogle ? '연동되어 있습니다.' : '연동되어 있지 않습니다.'}
            </p>
          </div>
          {hasGoogle ? (
            <Button variant="outline" onClick={unlinkGoogle} disabled={isPending}>
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              연동 해제
            </Button>
          ) : (
            <Button variant="outline" onClick={startGoogleLink}>
              연동하기
            </Button>
          )}
        </div>

        {failure && <p className="text-sm font-medium text-red-500">{failure}</p>}
      </CardContent>
    </Card>
  );
}

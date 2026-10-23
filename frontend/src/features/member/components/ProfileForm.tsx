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
import { useQueryClient } from '@tanstack/react-query';
import { renameProfileAction } from '../api/profile';
import { memberErrorMessage } from '../errors';
import { memberKeys } from '../hooks/keys';
import { ProfileFormValues, profileSchema } from '../schemas';
import { MyProfile } from '../types';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState, useTransition } from 'react';
import { useForm } from 'react-hook-form';

interface ProfileFormProps {
  initialProfile: MyProfile;
}

export default function ProfileForm({ initialProfile }: ProfileFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [isPending, startTransition] = useTransition();
  const [failure, setFailure] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
    reset,
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { displayName: initialProfile.displayName },
  });

  const onSubmit = (data: ProfileFormValues) => {
    startTransition(async () => {
      setFailure(null);
      const result = await renameProfileAction(data);
      if (!result.success) {
        setFailure(memberErrorMessage(result.error));
        return;
      }
      reset({ displayName: result.data.displayName });
      queryClient.setQueryData(memberKeys.profile(), result.data);
      router.refresh();
    });
  };

  return (
    <Card className="w-full max-w-2xl">
      <CardHeader>
        <CardTitle>프로필</CardTitle>
        <CardDescription>블로그와 댓글 등에 표시될 공개 프로필 정보입니다.</CardDescription>
      </CardHeader>

      <form onSubmit={handleSubmit(onSubmit)}>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="displayName">이름</Label>
            <Input
              id="displayName"
              placeholder="블로그와 댓글에 보일 이름"
              disabled={isPending}
              {...register('displayName')}
            />
            {(errors.displayName || failure) && (
              <p className="text-sm font-medium text-red-500">
                {errors.displayName?.message || failure}
              </p>
            )}
          </div>

          {/* 이메일 입력 필드 (읽기 전용) */}
          <div className="space-y-2">
            <Label htmlFor="email">이메일</Label>
            <Input
              id="email"
              value={initialProfile.email}
              disabled
              className="cursor-not-allowed bg-gray-100 text-gray-500"
            />
            <p className="text-[0.8rem] text-muted-foreground">
              이메일 주소는 아래 이메일 주소 항목에서 바꿉니다.
            </p>
          </div>
        </CardContent>

        <CardFooter className="flex justify-end border-t bg-gray-50/50 px-6 py-4">
          <Button
            type="submit"
            disabled={!isDirty || isPending}
            className="min-w-[100px] bg-indigo-600 text-white hover:bg-indigo-700 disabled:bg-gray-400 disabled:text-gray-700"
          >
            {isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                저장 중...
              </>
            ) : (
              '저장하기'
            )}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}

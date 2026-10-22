'use client';

import { updateUserProfileAction } from '@/actions/user';
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
import { ProfileFormValues, profileSchema } from '@/lib/schemas/userSchemas';
import { User } from '@/types/api/user';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTransition } from 'react';
import { useForm } from 'react-hook-form';

interface ProfileFormProps {
  initialUser: User;
}

export default function ProfileForm({ initialUser }: ProfileFormProps) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
    reset,
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      username: initialUser.username,
      nickname: initialUser.nickname,
    },
  });

  const onSubmit = (data: ProfileFormValues) => {
    startTransition(async () => {
      try {
        await updateUserProfileAction(data);

        alert('프로필이 성공적으로 업데이트되었습니다.');
        reset(data); // 폼 상태 초기화
        router.refresh(); // 페이지 새로고침
      } catch (error) {
        console.error('Failed to update profile:', error);
        alert('프로필 업데이트에 실패했습니다. 다시 시도해주세요.');
      }
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
            <Label htmlFor="nickname">닉네임</Label>
            <Input
              id="nickname"
              placeholder="닉네임을 입력하세요"
              disabled={isPending}
              {...register('nickname')}
            />
            {errors.nickname && (
              <p className="text-sm font-medium text-red-500">{errors.nickname.message}</p>
            )}
          </div>

          {/* 이메일 입력 필드 (읽기 전용) */}
          <div className="space-y-2">
            <Label htmlFor="email">이메일</Label>
            <Input
              id="email"
              value={initialUser.email}
              disabled
              className="cursor-not-allowed bg-gray-100 text-gray-500"
            />
            <p className="text-[0.8rem] text-muted-foreground">이메일 주소는 변경할 수 없습니다.</p>
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

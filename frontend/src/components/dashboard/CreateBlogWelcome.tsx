'use client';

import { useCreateBlogMutation } from '@/hooks/mutations/useUserMutations';
import { zodResolver } from '@hookform/resolvers/zod';
import { AlertTriangle, Loader2 } from 'lucide-react';
import { SubmitHandler, useForm } from 'react-hook-form';
import z from 'zod';
import { useRouter } from 'next/navigation';
import { Alert, AlertDescription, AlertTitle } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card } from '@/shared/ui/card';
import { Label } from '@/shared/ui/label';
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
  InputGroupText,
} from '@/shared/ui/input-group';

// 백엔드 CreateBlogRequest의 @Pattern("^[a-z0-9-]+$")과 동일하게 유지할 것 —
// 클라이언트가 더 느슨하면 인라인 검증 대신 제출 후 서버 에러로 떨어진다.
const schema = z.object({
  slug: z
    .string()
    .min(3, '3자 이상 입력해주세요.')
    .max(30, '30자 이하로 입력해주세요.')
    .regex(/^[a-z0-9-]+$/, '영문 소문자, 숫자, 하이픈(-)만 사용할 수 있습니다.'),
});

type FormValues = z.infer<typeof schema>;

export default function CreateBlogWelcome() {
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const router = useRouter();
  const { mutate: createBlog, isPending, error } = useCreateBlogMutation();
  const slugPreview = watch('slug');

  const onSubmit: SubmitHandler<FormValues> = (data) => {
    createBlog(data, {
      onSuccess: () => {
        router.replace('/dashboard');
      },
    });
  };

  return (
    <Card className="p-4 shadow-sm sm:p-6">
      <div className="mx-auto max-w-xl text-center">
        <h2 className="text-3xl font-bold tracking-tight text-gray-900 sm:text-4xl">
          당신의 공간을 만들어보세요
        </h2>
        <p className="mt-4 text-lg leading-8 text-gray-600">
          멋진 아이디어를 세상과 공유할 준비가 되셨나요? 사용할 블로그 주소를 만들어주세요.
        </p>

        <form onSubmit={handleSubmit(onSubmit)} className="mt-10 space-y-6">
          <div className="text-left">
            <Label htmlFor="slug" className="sr-only">
              블로그 주소
            </Label>
            <InputGroup>
              <InputGroupAddon>
                <InputGroupText>
                  {/* 모바일에선 addon이 한 줄을 다 차지해 입력칸이 깨지므로 @만 노출 */}
                  <span className="hidden sm:inline">https://contentria.com/</span>@
                </InputGroupText>
              </InputGroupAddon>
              <InputGroupInput
                id="slug"
                placeholder="your-blog-name"
                autoFocus
                autoComplete="off"
                aria-invalid={!!errors.slug}
                aria-describedby={errors.slug ? 'slug-error' : undefined}
                {...register('slug')}
              />
            </InputGroup>
            {errors.slug && (
              <p id="slug-error" className="mt-2 text-sm font-medium text-destructive">
                {errors.slug.message}
              </p>
            )}
            {/* 모바일에선 addon이 @만 보이므로, 만들어질 전체 주소를 실시간으로 보여준다 */}
            <p className="mt-2 break-all text-sm text-gray-500">
              내 블로그 주소:{' '}
              <span className="font-medium text-primary">
                https://contentria.com/@{slugPreview || 'your-blog-name'}
              </span>
            </p>
          </div>

          {error && (
            <Alert variant="destructive" className="text-left">
              <AlertTriangle className="h-4 w-4" />
              <AlertTitle>블로그 생성에 실패했습니다</AlertTitle>
              <AlertDescription>{error.message}</AlertDescription>
            </Alert>
          )}

          <Button type="submit" size="lg" className="w-full" disabled={isPending}>
            {isPending ? (
              <>
                <Loader2 className="animate-spin" />
                생성 중...
              </>
            ) : (
              '내 블로그 생성하기'
            )}
          </Button>
        </form>

        <p className="mt-6 text-sm text-gray-500">
          블로그 주소는 나중에 변경할 수 없으니 신중하게 선택해주세요.
        </p>
      </div>
    </Card>
  );
}

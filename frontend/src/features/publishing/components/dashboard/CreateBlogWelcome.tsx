'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { AlertTriangle, Loader2 } from 'lucide-react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { useState, useTransition } from 'react';
import { createBlogAction } from '../../api/blogs';
import { publishingErrorMessage } from '../../errors';
import { createBlogSchema, CreateBlogFormValues } from '../../schemas';
import { Input } from '@/shared/ui/input';
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

export default function CreateBlogWelcome() {
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<CreateBlogFormValues>({
    resolver: zodResolver(createBlogSchema),
  });

  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);
  const slugPreview = watch('slug');

  const onSubmit: SubmitHandler<CreateBlogFormValues> = (data) => {
    startTransition(async () => {
      setError(null);
      const result = await createBlogAction(data);
      if (result.success) router.replace('/dashboard');
      else setError(publishingErrorMessage(result.error));
    });
  };

  return (
    <Card className="p-4 shadow-sm sm:p-6">
      <div className="mx-auto max-w-xl text-center">
        <h2 className="text-3xl font-bold tracking-tight text-gray-900 sm:text-4xl">
          당신의 공간을 만들어보세요
        </h2>
        <p className="mt-4 text-lg leading-8 text-gray-600">
          멋진 아이디어를 세상과 공유할 준비가 되셨나요? 블로그 주소와 이름을 정해주세요.
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

          <div className="text-left">
            <Label htmlFor="title">블로그 이름</Label>
            <Input
              id="title"
              placeholder="나의 블로그"
              autoComplete="off"
              className="mt-2"
              aria-invalid={!!errors.title}
              aria-describedby={errors.title ? 'title-error' : undefined}
              {...register('title')}
            />
            {errors.title && (
              <p id="title-error" className="mt-2 text-sm font-medium text-destructive">
                {errors.title.message}
              </p>
            )}
          </div>

          {error && (
            <Alert variant="destructive" className="text-left">
              <AlertTriangle className="h-4 w-4" />
              <AlertTitle>블로그 생성에 실패했습니다</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
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

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
import { Textarea } from '@/shared/ui/textarea';
import { changeBlogSettingsAction } from '../../api/blogs';
import { publishingErrorMessage } from '../../errors';
import { blogSettingsSchema, BlogSettingsFormValues } from '../../schemas';
import { BlogView } from '../../types';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTransition } from 'react';
import { useForm } from 'react-hook-form';

interface BlogSettingsFormProps {
  blog: BlogView;
}

/** 블로그 공개 정보(제목/설명) 수정 폼 — 주소(slug)는 URL 정체성이라 읽기 전용. */
export default function BlogSettingsForm({ blog }: BlogSettingsFormProps) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
    reset,
  } = useForm<BlogSettingsFormValues>({
    resolver: zodResolver(blogSettingsSchema),
    defaultValues: {
      title: blog.title,
      description: blog.description ?? '',
    },
  });

  const onSubmit = (data: BlogSettingsFormValues) => {
    startTransition(async () => {
      const result = await changeBlogSettingsAction(blog.slug, {
        title: data.title,
        description: data.description.trim() === '' ? null : data.description,
      });
      if (!result.success) {
        alert(publishingErrorMessage(result.error));
        return;
      }
      alert('블로그 설정이 저장되었습니다.');
      reset(data);
      router.refresh();
    });
  };

  return (
    <Card className="w-full max-w-2xl">
      <CardHeader>
        <CardTitle>블로그 설정</CardTitle>
        <CardDescription>
          블로그 공개 페이지와 검색 결과(탭 제목)에 표시되는 정보입니다.
        </CardDescription>
      </CardHeader>

      <form onSubmit={handleSubmit(onSubmit)}>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="blog-address">블로그 주소</Label>
            <Input
              id="blog-address"
              value={`contentria.com/@${blog.slug}`}
              disabled
              className="cursor-not-allowed bg-gray-100 text-gray-500"
            />
            <p className="text-[0.8rem] text-muted-foreground">블로그 주소는 변경할 수 없습니다.</p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="blog-title">블로그 제목</Label>
            <Input
              id="blog-title"
              placeholder="블로그 제목을 입력하세요"
              disabled={isPending}
              {...register('title')}
            />
            {errors.title && (
              <p className="text-sm font-medium text-red-500">{errors.title.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="blog-description">블로그 설명</Label>
            <Textarea
              id="blog-description"
              placeholder="블로그를 소개하는 한두 문장을 적어보세요 (선택)"
              rows={3}
              disabled={isPending}
              {...register('description')}
            />
            {errors.description && (
              <p className="text-sm font-medium text-red-500">{errors.description.message}</p>
            )}
          </div>
        </CardContent>

        <CardFooter className="flex justify-end border-t bg-gray-50/50 px-6 py-4">
          <Button type="submit" disabled={!isDirty || isPending} className="min-w-[100px]">
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

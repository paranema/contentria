import { z } from 'zod';

// As publishing takes them (01-rules, 처음 정하는 값). Looser here would only turn inline errors
// into errors after submitting.

const title = z
  .string()
  .trim()
  .min(1, '블로그 제목을 입력해주세요.')
  .max(50, '블로그 제목은 50자 이하로 입력해주세요.');

export const createBlogSchema = z.object({
  slug: z
    .string()
    .min(3, '3자 이상 입력해주세요.')
    .max(30, '30자 이하로 입력해주세요.')
    .regex(/^[a-z0-9-]+$/, '영문 소문자, 숫자, 하이픈(-)만 사용할 수 있습니다.'),
  title,
});

export const blogSettingsSchema = z.object({
  title,
  description: z.string().max(300, '블로그 설명은 300자 이하로 입력해주세요.'),
});

export type CreateBlogFormValues = z.infer<typeof createBlogSchema>;
export type BlogSettingsFormValues = z.infer<typeof blogSettingsSchema>;

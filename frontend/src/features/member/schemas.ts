import { z } from 'zod';

export const emailStepSchema = z.object({
  email: z
    .string()
    .min(1, { message: 'Please enter your email address.' })
    .email({ message: 'Please enter a valid email address.' }),
});

/** API 규칙과 BCrypt 최대 입력 길이에 맞춰 새 비밀번호를 8~72자로 검증합니다. */
export const newPasswordSchema = z.object({
  password: z
    .string()
    .min(8, { message: 'Use at least 8 characters.' })
    .max(72, { message: 'Use at most 72 characters.' }),
});

/** 기존 비밀번호 입력 여부만 확인하며, 일치 여부는 API에서 검증합니다. */
export const passwordSchema = z.object({
  password: z.string().min(1, { message: 'Please enter your password.' }).max(72),
});

/** 회원 도메인 규칙에 따라 앞뒤 공백 제거 후 2~50자인 표시 이름을 검증합니다. */
export const profileSchema = z.object({
  displayName: z
    .string()
    .trim()
    .min(2, '이름은 2자 이상이어야 합니다.')
    .max(50, '이름은 50자 이하여야 합니다.'),
});

/** 대시보드 설정 화면의 비밀번호 변경 폼 스키마입니다. (01-rules 흐름 3, 4) */
export const passwordChangeSchema = z.object({
  currentPassword: z.string().min(1, '현재 비밀번호를 입력해 주세요.').max(72),
  newPassword: z
    .string()
    .min(8, '새 비밀번호는 8자 이상이어야 합니다.')
    .max(72, '새 비밀번호는 72자 이하여야 합니다.'),
});

export const emailChangeSchema = z.object({
  newEmail: z
    .string()
    .min(1, '새 이메일 주소를 입력해 주세요.')
    .email('이메일 주소 형식이 아닙니다.'),
});

export type EmailStepFormData = z.infer<typeof emailStepSchema>;
export type NewPasswordFormData = z.infer<typeof newPasswordSchema>;
export type PasswordFormData = z.infer<typeof passwordSchema>;
export type ProfileFormValues = z.infer<typeof profileSchema>;
export type PasswordChangeFormValues = z.infer<typeof passwordChangeSchema>;
export type EmailChangeFormValues = z.infer<typeof emailChangeSchema>;

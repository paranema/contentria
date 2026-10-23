import { z } from 'zod';

export const emailStepSchema = z.object({
  email: z
    .string()
    .min(1, { message: 'Please enter your email address.' })
    .email({ message: 'Please enter a valid email address.' }),
});

/** As the API takes a new password: 8 to 72 characters, the most BCrypt reads. */
export const newPasswordSchema = z.object({
  password: z
    .string()
    .min(8, { message: 'Use at least 8 characters.' })
    .max(72, { message: 'Use at most 72 characters.' }),
});

/** An existing password: whether it matches is the API's call. */
export const passwordSchema = z.object({
  password: z.string().min(1, { message: 'Please enter your password.' }).max(72),
});

/** As the member domain takes a display name: 2 to 50 characters after trimming. */
export const profileSchema = z.object({
  displayName: z
    .string()
    .trim()
    .min(2, '이름은 2자 이상이어야 합니다.')
    .max(50, '이름은 50자 이하여야 합니다.'),
});

/** The settings screen's forms (01-rules 흐름 3, 4), worded for the dashboard. */
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

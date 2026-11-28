'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import { ApiError } from '@/shared/api/apiError';
import apiServer from '@/shared/api/apiServer';
import { revalidatePath } from 'next/cache';
import { cache } from 'react';
import { profileSchema } from '../schemas';
import { MyProfile, PublicProfile, RenameProfileRequest } from '../types';

/** 로그인한 회원의 프로필을 조회하며, 401 응답 시 로그인 페이지로 리다이렉트합니다. */
export const getMyProfile = cache(async () => apiServer.get<MyProfile>('/api/me/profile'));

/** 로그인한 회원의 프로필을 조회하며, 비로그인 상태(401)이면 공개 화면을 위해 null을 반환합니다. */
export const getMyProfileOrNull = cache(async (): Promise<MyProfile | null> => {
  try {
    return await apiServer.get<MyProfile>('/api/me/profile', { shouldRedirectOn401: false });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
});

/**
 * 게시글 작성자나 블로그 소유자의 공개 프로필을 계정 ID 목록으로 일괄 조회합니다.
 * 존재하지 않는 계정은 결과에서 제외됩니다.
 */
export async function getPublicProfiles(accountIds: readonly string[]): Promise<PublicProfile[]> {
  const ids = [...new Set(accountIds)];
  if (ids.length === 0) return [];
  return apiServer.get<PublicProfile[]>(
    `/api/members/profiles?ids=${ids.map(encodeURIComponent).join(',')}`,
    {
      requireAuth: false,
    }
  );
}

/** 표시 이름을 변경합니다. 다른 회원이 사용 중인 이름이면 DISPLAY_NAME_TAKEN 에러가 발생합니다. (흐름 5) */
export async function renameProfileAction(
  request: RenameProfileRequest
): Promise<ActionResult<MyProfile>> {
  try {
    const profile = await apiServer.patch<MyProfile>(
      '/api/me/profile',
      profileSchema.parse(request)
    );
    revalidatePath('/dashboard/settings');
    return { success: true, data: profile };
  } catch (error) {
    return toActionError(error);
  }
}

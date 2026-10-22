'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import { ApiError } from '@/shared/api/apiError';
import apiServer from '@/shared/api/apiServer';
import { revalidatePath } from 'next/cache';
import { cache } from 'react';
import { profileSchema } from '../schemas';
import { MyProfile, PublicProfile, RenameProfileRequest } from '../types';

/** The signed-in member's profile; a 401 sends the browser to the login page. */
export const getMyProfile = cache(async () => apiServer.get<MyProfile>('/api/me/profile'));

/** The same, or null for someone not signed in: for screens anyone may see. */
export const getMyProfileOrNull = cache(async (): Promise<MyProfile | null> => {
  try {
    return await apiServer.get<MyProfile>('/api/me/profile', { shouldRedirectOn401: false });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
});

/**
 * Anyone's public profile, by account id: who wrote a post or owns a blog. Profiles that do not
 * exist are simply missing from the answer. Asked once for every member on a screen.
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

/** 흐름 5: a new display name. Fails with DISPLAY_NAME_TAKEN when another member has it. */
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

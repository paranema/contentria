import { getMyProfileOrNull } from '@/features/member/api/profile';
import type { Viewer } from '@/features/interaction/components/SubscribeButton';

/** Who is looking at someone's blog: not signed in, its owner, or another member. */
export async function viewerOf(ownerId: string) {
  const profile = await getMyProfileOrNull();
  const viewer: Viewer = !profile
    ? 'anonymous'
    : profile.accountId === ownerId
      ? 'owner'
      : 'member';
  return { profile, viewer };
}

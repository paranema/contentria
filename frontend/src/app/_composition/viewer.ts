import { getMyProfileOrNull } from '@/features/member/api/profile';
import type { Viewer } from '@/features/interaction/components/SubscribeButton';

/** 블로그 조회자의 상태(비로그인, 소유자, 일반 회원)와 내 프로필을 판별합니다. */
export async function viewerOf(ownerId: string) {
  const profile = await getMyProfileOrNull();
  const viewer: Viewer = !profile
    ? 'anonymous'
    : profile.accountId === ownerId
      ? 'owner'
      : 'member';
  return { profile, viewer };
}

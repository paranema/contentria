// 로그인이나 이름 변경 후 관련 캐시를 갱신하기 위한 회원 기능의 TanStack Query 키입니다.
export const memberKeys = {
  all: ['member'] as const,
  profile: () => [...memberKeys.all, 'profile'] as const,
};

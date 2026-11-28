'use client';

import { usePublicConfig } from '@/shared/providers/PublicConfigProvider';
import { useCallback } from 'react';

/**
 * 브라우저 페이지 이동을 통해 Google 로그인 또는 계정 연동을 시작합니다.
 *
 * 브라우저가 직접 이동하므로 클러스터 내부용 `INTERNAL_API_BASE_URL`이 아닌 공개 API 오리진
 * (`publicApiBaseUrl`, Envoy Gateway 뒤 동일 출처 `/api/...`일 때 기본값 `''`)을 사용합니다.
 */
export function useGoogleOAuth() {
  const { publicApiBaseUrl } = usePublicConfig();

  const startGoogleLogin = useCallback(() => {
    window.location.href = `${publicApiBaseUrl}/api/oauth2/authorization/google`;
  }, [publicApiBaseUrl]);

  const startGoogleLink = useCallback(() => {
    window.location.href = `${publicApiBaseUrl}/api/oauth2/authorization/google?intent=link`;
  }, [publicApiBaseUrl]);

  return { startGoogleLogin, startGoogleLink };
}

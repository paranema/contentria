'use client';

import { usePublicConfig } from '@/shared/providers/PublicConfigProvider';
import { useCallback } from 'react';

/**
 * Starts Google sign-in or account linking through a browser navigation.
 *
 * The browser itself goes there, so this uses the API's public address (`externalApiBaseUrl`,
 * `/api` included — defaults to same-origin `/api` behind Envoy Gateway), not the in-cluster
 * `API_BASE_URL`.
 */
export function useGoogleOAuth() {
  const { externalApiBaseUrl } = usePublicConfig();

  const startGoogleLogin = useCallback(() => {
    window.location.href = `${externalApiBaseUrl}/oauth2/authorization/google`;
  }, [externalApiBaseUrl]);

  const startGoogleLink = useCallback(() => {
    window.location.href = `${externalApiBaseUrl}/oauth2/authorization/google?intent=link`;
  }, [externalApiBaseUrl]);

  return { startGoogleLogin, startGoogleLink };
}

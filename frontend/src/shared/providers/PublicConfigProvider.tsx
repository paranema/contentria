'use client';

import { PublicConfig } from '@/shared/config/runtimeConfig';
import { createContext, useContext } from 'react';

const DEFAULT_PUBLIC_CONFIG: PublicConfig = {
  publicApiBaseUrl: '',
  recaptchaV3SiteKey: '',
  recaptchaV2SiteKey: '',
};

const PublicConfigContext = createContext<PublicConfig>(DEFAULT_PUBLIC_CONFIG);

export function PublicConfigProvider({
  config,
  children,
}: {
  config: PublicConfig;
  children: React.ReactNode;
}) {
  return <PublicConfigContext.Provider value={config}>{children}</PublicConfigContext.Provider>;
}

export function usePublicConfig(): PublicConfig {
  return useContext(PublicConfigContext);
}

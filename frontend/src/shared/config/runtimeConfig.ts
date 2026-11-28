export interface PublicConfig {
  publicApiBaseUrl: string;
  recaptchaV3SiteKey: string;
  recaptchaV2SiteKey: string;
}

/**
 * Next.js 서버(SSR, Server Action, proxy)가 백엔드를 호출할 때 사용하는 내부 주소입니다(`/api` 제외).
 * 빌드 시점에 값이 고정되지 않도록 런타임 함수로 조회합니다.
 */
export function getApiBaseUrl(env: NodeJS.ProcessEnv = process.env): string {
  return stripTrailingSlash(env.INTERNAL_API_BASE_URL?.trim() || 'http://localhost:8080');
}

/**
 * 루트 서버 레이아웃에서 요청 시점에 읽어 브라우저(`PublicConfigProvider`)로 전달하는 공개 설정입니다.
 * 단일 컨테이너 이미지를 ConfigMap 주입만으로 환경(dev, prod)마다 다르게 구동할 수 있습니다.
 */
export function readPublicConfig(env: NodeJS.ProcessEnv = process.env): PublicConfig {
  return {
    publicApiBaseUrl: stripTrailingSlash(env.PUBLIC_API_BASE_URL?.trim() || ''),
    recaptchaV3SiteKey: env.RECAPTCHA_V3_SITE_KEY?.trim() || '',
    recaptchaV2SiteKey: env.RECAPTCHA_V2_SITE_KEY?.trim() || '',
  };
}

function stripTrailingSlash(url: string): string {
  return url.replace(/\/+$/, '');
}

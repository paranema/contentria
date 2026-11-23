export interface PublicConfig {
  externalApiBaseUrl: string;
  recaptchaV3SiteKey: string;
  recaptchaV2CheckboxSiteKey: string;
}

/**
 * Next.js 서버(SSR, Server Action, proxy)가 백엔드를 호출하는 내부 주소(/api 제외).
 * 빌드 시점에 고정되지 않도록 런타임 함수로 조회한다.
 */
export function getApiBaseUrl(env: NodeJS.ProcessEnv = process.env): string {
  return stripTrailingSlash(env.API_BASE_URL?.trim() || 'http://localhost:8080');
}

/**
 * 루트 서버 레이아웃에서 요청 시점에 읽어 브라우저(PublicConfigProvider)로 전달하는 공개 설정.
 * 단일 컨테이너 이미지를 ConfigMap만으로 환경(dev, prod)마다 다르게 구동할 수 있다.
 */
export function readPublicConfig(env: NodeJS.ProcessEnv = process.env): PublicConfig {
  return {
    externalApiBaseUrl: stripTrailingSlash(env.EXTERNAL_API_BASE_URL?.trim() || '/api'),
    recaptchaV3SiteKey: env.RECAPTCHA_V3_SITE_KEY?.trim() || '',
    recaptchaV2CheckboxSiteKey: env.RECAPTCHA_V2_CHECKBOX_SITE_KEY?.trim() || '',
  };
}

function stripTrailingSlash(url: string): string {
  return url.length > 1 ? url.replace(/\/+$/, '') : url;
}

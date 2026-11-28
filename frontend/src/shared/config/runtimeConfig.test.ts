import { describe, expect, it } from 'vitest';
import { getApiBaseUrl, readPublicConfig } from './runtimeConfig';

describe('runtimeConfig', () => {
  it('uses local and same-origin defaults when unset', () => {
    const env = {} as NodeJS.ProcessEnv;

    expect(getApiBaseUrl(env)).toBe('http://localhost:8080');
    expect(readPublicConfig(env)).toEqual({
      publicApiBaseUrl: '',
      recaptchaV3SiteKey: '',
      recaptchaV2SiteKey: '',
    });
  });

  it('reads runtime values and strips trailing slashes', () => {
    const env = {
      INTERNAL_API_BASE_URL: 'http://contentria-backend-api:8080/',
      PUBLIC_API_BASE_URL: 'https://dev.contentria.com/',
      RECAPTCHA_V3_SITE_KEY: 'v3-key',
      RECAPTCHA_V2_SITE_KEY: 'v2-key',
    } as unknown as NodeJS.ProcessEnv;

    expect(getApiBaseUrl(env)).toBe('http://contentria-backend-api:8080');
    expect(readPublicConfig(env)).toEqual({
      publicApiBaseUrl: 'https://dev.contentria.com',
      recaptchaV3SiteKey: 'v3-key',
      recaptchaV2SiteKey: 'v2-key',
    });
  });
});

/**
 * Starts Google sign-in, the one entry point the login and sign-up screens share.
 *
 * The browser itself goes there, so this must be the API's public address
 * (NEXT_PUBLIC_EXTERNAL_API_BASE_URL, `/api` included), not the in-cluster NEXT_PUBLIC_API_BASE_URL.
 * The API sends the browser back to /login/callback with the session cookies set, or to
 * /login?error=<code>.
 */
export function startGoogleLogin(): void {
  const baseUrl = process.env.NEXT_PUBLIC_EXTERNAL_API_BASE_URL;
  if (baseUrl) {
    window.location.href = `${baseUrl}/oauth2/authorization/google`;
  } else {
    console.error('External API base URL is not configured.');
  }
}

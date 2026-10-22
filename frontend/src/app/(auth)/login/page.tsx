import LoginContainer from '@/features/member/components/login/LoginContainer';
import { memberErrorMessage } from '@/features/member/errors';

/**
 * `?error=<code>`: a failed Google sign-in, as the API sends the browser back.
 * `?alert=session_expired`: proxy.ts or apiServer found the session over.
 */
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string; alert?: string }>;
}) {
  const { error, alert } = await searchParams;
  const notice = error
    ? memberErrorMessage({ code: error })
    : alert === 'session_expired'
      ? 'Your session has ended. Please sign in again.'
      : null;
  return <LoginContainer notice={notice} />;
}

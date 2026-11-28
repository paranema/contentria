import LoginContainer from '@/features/member/components/login/LoginContainer';
import { memberErrorMessage } from '@/features/member/errors';

/**
 * 쿼리 파라미터에 따른 안내 메시지를 표시합니다.
 * - `?error=<code>`: Google 로그인 실패 시 API 서버가 리다이렉트하며 전달한 오류 코드입니다.
 * - `?alert=session_expired`: proxy.ts 또는 apiServer에서 세션 만료를 감지한 경우입니다.
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

import { getMyBlogs } from '@/features/publishing/api/blogs';
import { getMyCredentials } from '@/features/member/api/account';
import { getMyProfile } from '@/features/member/api/profile';
import BlogSettingsForm from '@/features/publishing/components/dashboard/BlogSettingsForm';
import EmailChangeCard from '@/features/member/components/account/EmailChangeCard';
import LoginMethodsCard from '@/features/member/components/account/LoginMethodsCard';
import PasswordCard from '@/features/member/components/account/PasswordCard';
import WithdrawalCard from '@/features/member/components/account/WithdrawalCard';
import ProfileForm from '@/features/member/components/ProfileForm';
import { accountErrorMessage } from '@/features/member/errors';
import { Alert, AlertDescription } from '@/shared/ui/alert';

/**
 * Google 계정 연동 후 API 서버가 리다이렉트(`?linked=true` 또는 `?error=<code>`, MEMBER_SOCIAL_LINK_RESULT_URL)하는 설정 페이지입니다.
 */
export default async function SettingsPage({
  searchParams,
}: {
  searchParams: Promise<{ linked?: string; error?: string }>;
}) {
  const { linked, error } = await searchParams;
  const [profile, providers, blogs] = await Promise.all([
    getMyProfile(),
    getMyCredentials(),
    getMyBlogs(),
  ]);
  // 블로그를 개설하지 않은 독자 회원도 계정 설정은 관리할 수 있습니다.
  const blog = blogs[0];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900">설정</h1>
        <p className="text-muted-foreground">블로그와 계정 설정을 관리하세요.</p>
      </div>

      <div className="border-b border-gray-200" />

      {linked === 'true' && (
        <Alert className="max-w-2xl">
          <AlertDescription>Google 계정을 연동했습니다.</AlertDescription>
        </Alert>
      )}
      {error && (
        <Alert variant="destructive" className="max-w-2xl">
          <AlertDescription>{accountErrorMessage({ code: error })}</AlertDescription>
        </Alert>
      )}

      {blog && <BlogSettingsForm blog={blog} />}

      <ProfileForm initialProfile={profile} />

      <LoginMethodsCard providers={providers} />

      <PasswordCard hasPassword={providers.includes('EMAIL')} email={profile.email} />

      <EmailChangeCard email={profile.email} />

      <WithdrawalCard email={profile.email} />
    </div>
  );
}

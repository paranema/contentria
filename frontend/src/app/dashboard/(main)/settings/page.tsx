import { getMyBlogs } from '@/features/publishing/api/blogs';
import { getMyProfile } from '@/features/member/api/profile';
import BlogSettingsForm from '@/features/publishing/components/dashboard/BlogSettingsForm';
import ProfileForm from '@/features/member/components/ProfileForm';

export default async function SettingsPage() {
  const profile = await getMyProfile();
  // (main) 레이아웃 가드가 블로그 존재를 보장하므로 첫 블로그를 사용
  const blog = (await getMyBlogs())[0];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900">설정</h1>
        <p className="text-muted-foreground">블로그와 계정 프로필 설정을 관리하세요.</p>
      </div>

      <div className="border-b border-gray-200" />

      {blog && <BlogSettingsForm blog={blog} />}

      <ProfileForm initialProfile={profile} />
    </div>
  );
}

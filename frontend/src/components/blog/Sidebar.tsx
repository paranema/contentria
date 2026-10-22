import Image from 'next/image';
import CategoryItem from './CategoryItem';
import { BlogResponse } from '@/types/api/blogs';
import { getHighResGoogleProfileImage } from '@/lib/imageUtil';
import { OwnerResponse } from '@/types/api/user';
import { CategoryResponse } from '@/types/api/category';
import { buildCategoryTree } from '@/lib/categoryTree';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { Separator } from '@/shared/ui/separator';

interface SidebarProps {
  blog: BlogResponse;
  owner: OwnerResponse;
  categories: CategoryResponse[];
}

export default function Sidebar({ blog, owner, categories }: SidebarProps) {
  const profileImageUrl =
    getHighResGoogleProfileImage(owner.profileImageUrl, 256) ?? '/images/default-profile.png';
  const blogDescription = blog.description ?? '블로그에 오신 것을 환영합니다.';

  const categoryTree = buildCategoryTree(categories, blog.slug);

  return (
    <aside className="mb-10 w-full shrink-0 lg:sticky lg:top-24 lg:mb-0 lg:h-[calc(100vh-8rem)] lg:w-72 lg:overflow-y-auto lg:pr-4">
      {/* 프로필 박스 (Shadcn Card 활용) */}
      <Card className="mb-6 shadow-sm">
        <CardContent className="flex flex-col items-center p-6 text-center">
          <div className="relative mb-5 flex h-32 w-32 overflow-hidden rounded-full border border-gray-100 bg-gray-50 sm:h-40 sm:w-40">
            <Image
              src={profileImageUrl}
              alt={`${owner.nickname} profile`}
              fill
              style={{ objectFit: 'cover' }}
              sizes="(max-width: 768px) 128px, 160px"
              priority
            />
          </div>
          <h2 className="mb-2 text-xl font-bold text-gray-900">{owner.nickname}</h2>
          <p className="text-sm text-muted-foreground">{blogDescription}</p>
        </CardContent>
      </Card>

      {/* 카테고리 박스 */}
      {categories.length > 0 && (
        <Card className="shadow-sm">
          <CardHeader className="pb-3 text-center">
            <CardTitle className="text-lg font-bold text-indigo-600">카테고리</CardTitle>
          </CardHeader>
          <Separator />
          <CardContent className="p-4">
            <ul className="space-y-1">
              {categoryTree.map((node) => (
                <CategoryItem key={node.id} category={node} blogSlug={blog.slug} />
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </aside>
  );
}

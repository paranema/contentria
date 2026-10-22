import Image from 'next/image';
import CategoryItem from './CategoryItem';
import { getHighResGoogleProfileImage } from '@/shared/lib/imageUtil';
import { buildCategoryTree } from '../../categoryTree';
import { BlogView, CategoryView } from '../../types';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { Separator } from '@/shared/ui/separator';

/** The blog owner as the page put it together from member's public profile. */
export interface BlogOwner {
  displayName: string;
  pictureUrl: string | null;
}

interface SidebarProps {
  blog: BlogView;
  owner: BlogOwner | null;
  categories: CategoryView[];
}

export default function Sidebar({ blog, owner, categories }: SidebarProps) {
  const profileImageUrl =
    getHighResGoogleProfileImage(owner?.pictureUrl ?? null, 256) ?? '/images/default-profile.png';
  const blogDescription = blog.description ?? '블로그에 오신 것을 환영합니다.';

  const categoryTree = buildCategoryTree(categories);

  return (
    <aside className="mb-10 w-full shrink-0 lg:sticky lg:top-24 lg:mb-0 lg:h-[calc(100vh-8rem)] lg:w-72 lg:overflow-y-auto lg:pr-4">
      {/* 프로필 박스 (Shadcn Card 활용) */}
      <Card className="mb-6 shadow-sm">
        <CardContent className="flex flex-col items-center p-6 text-center">
          <div className="relative mb-5 flex h-32 w-32 overflow-hidden rounded-full border border-gray-100 bg-gray-50 sm:h-40 sm:w-40">
            <Image
              src={profileImageUrl}
              alt={`${owner?.displayName ?? blog.title} profile`}
              fill
              style={{ objectFit: 'cover' }}
              sizes="(max-width: 768px) 128px, 160px"
              priority
            />
          </div>
          <h2 className="mb-2 text-xl font-bold text-gray-900">
            {owner?.displayName ?? blog.title}
          </h2>
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

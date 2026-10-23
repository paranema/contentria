import Image from 'next/image';
import { UserIcon } from 'lucide-react';
import { MyProfile } from '@/features/member/types';

interface UserAvatarProps {
  user: MyProfile | null | undefined;
  size?: number;
  /** circle(기본): 헤더 등 작은 아바타 / square: 프로필 카드처럼 영역을 가득 채울 때 */
  shape?: 'circle' | 'square';
}

/**
 * 구글 OAuth 프로필 URL은 끝의 크기 파라미터(=s96-c) 때문에 기본 96px로 내려와
 * 크게 그리면 화질이 깨진다. 표시 크기에 맞춰(레티나 2배, 96~512 범위) 파라미터를
 * 키워 고해상도를 요청한다. 구글 URL 패턴이 아니면 원본 그대로 둔다.
 */
function withDisplaySize(url: string, size: number): string {
  const target = Math.min(Math.max(size * 2, 96), 512);
  return url.replace(/=s\d+(-c)?$/, `=s${target}-c`);
}

const UserAvatar = ({ user, size = 24, shape = 'circle' }: UserAvatarProps) => {
  const iconSize = size * 0.6;
  const rounded = shape === 'circle' ? 'rounded-full' : '';

  return (
    <>
      {user?.pictureUrl ? (
        <Image
          src={withDisplaySize(user.pictureUrl, size)}
          alt={user.displayName || 'User Avatar'}
          fill
          sizes={`${size}px`}
          className={`${rounded} object-cover`}
        />
      ) : (
        <div className={`flex h-full w-full items-center justify-center bg-gray-200 ${rounded}`}>
          <UserIcon size={iconSize} className="text-gray-600" />
        </div>
      )}
    </>
  );
};

export default UserAvatar;

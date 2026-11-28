import Image from 'next/image';
import { UserIcon } from 'lucide-react';
import { MyProfile } from '@/features/member/types';

interface UserAvatarProps {
  user: MyProfile | null | undefined;
  size?: number;
  /** 아바타 형태를 지정합니다. circle(기본값)은 원형, square는 영역을 가득 채우는 정사각형입니다. */
  shape?: 'circle' | 'square';
}

/**
 * Google OAuth 프로필 URL의 기본 크기 파라미터(=s96-c)를 레티나 2배 크기(96~512px)로 교체해 고해상도 이미지를 요청합니다.
 * Google URL 패턴이 아니면 원본 URL을 그대로 반환합니다.
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

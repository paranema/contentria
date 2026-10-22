import { User } from 'lucide-react';
import { UserAvatarProps } from './types';
import Image from 'next/image';

const UserAvatar = ({ user, size = 'md' }: UserAvatarProps) => {
  const sizeClasses = {
    sm: 'h-10 w-10 border',
    md: 'h-11 w-11 border-2',
  };

  const iconSize = {
    sm: 20,
    md: 24,
  };

  return (
    <div
      className={`relative flex items-center justify-center overflow-hidden rounded-full bg-gray-200 ${sizeClasses[size]}`}
    >
      {user?.pictureUrl ? (
        <Image
          src={user.pictureUrl}
          alt={user.displayName}
          fill // 부모 요소에 맞춰 채우기
          sizes="(max-width: 768px) 10vw, (max-width: 1200px) 5vw, 44px" // 뷰포트에 따른 이미지 크기 힌트
          className="h-full w-full object-cover"
        />
      ) : (
        <User size={iconSize[size]} className="text-gray-600" />
      )}
    </div>
  );
};

export default UserAvatar;

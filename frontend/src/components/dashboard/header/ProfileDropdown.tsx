import { MyProfile } from '@/features/member/types';
import UserAvatar from './UserAvatar';
import { Home, LogOut, Settings } from 'lucide-react';
import Link from 'next/link';

interface ProfileDropdownProps {
  user: MyProfile | null | undefined;
  onClose: () => void;
  onLogout: () => void;
}

const ProfileDropdown = ({ user, onClose, onLogout }: ProfileDropdownProps) => {
  return (
    <div className="absolute right-0 z-20 mt-2 w-64 rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none">
      {/* Profile info */}
      <div className="border-b border-gray-100 px-4 py-3">
        <div className="flex items-center">
          <div className="relative h-10 w-10 flex-shrink-0 overflow-hidden rounded-full border border-gray-200">
            <UserAvatar user={user} size={40} />
          </div>
          <div className="ml-3 min-w-0 text-left">
            <p className="truncate text-sm font-medium text-gray-800">
              {user?.displayName || '사용자'}
            </p>
            <p className="truncate text-xs text-gray-500">{user?.email || ''}</p>
          </div>
        </div>
      </div>

      {/* Menu items */}
      <div className="py-1">
        <Link
          href="/dashboard/settings"
          className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
          onClick={onClose}
        >
          <Settings size={16} className="mr-3 text-gray-500" />
          <span>설정</span>
        </Link>
        <Link
          href="/"
          className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
          onClick={onClose}
        >
          <Home size={16} className="mr-3 text-gray-500" />
          <span>홈</span>
        </Link>
      </div>

      {/* Logout button */}
      <div className="border-t border-gray-100 py-1">
        <button
          onClick={() => {
            onLogout();
            onClose();
          }}
          className="flex w-full items-center px-4 py-2 text-left text-sm text-gray-600 hover:bg-gray-100"
        >
          <LogOut size={16} className="mr-3 text-gray-500" />
          <span>로그아웃</span>
        </button>
      </div>
    </div>
  );
};

export default ProfileDropdown;

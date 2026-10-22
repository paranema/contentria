import Link from 'next/link';
import { Home, LayoutDashboard, LogOut } from 'lucide-react';
import { ProfileDropdownProps } from './types';
import UserAvatar from './UserAvatar';

const ProfileDropdown = ({ user, isOpen, onClose, onLogout }: ProfileDropdownProps) => {
  if (!isOpen) {
    return null;
  }

  return (
    <div className="absolute right-0 z-10 mt-2 w-64 rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none">
      {/* 프로필 정보 */}
      <div className="border-b border-gray-100 px-4 py-3">
        <div className="flex items-center">
          <UserAvatar user={user} size="sm" />
          <div className="ml-3 text-left">
            <div className="text-sm font-medium text-gray-800">{user.displayName}</div>
            <div className="text-xs text-gray-500">{user.email}</div>
          </div>
        </div>
      </div>

      {/* 메뉴 항목 */}
      <div className="border-b border-gray-100">
        <Link
          href="/"
          className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
          onClick={onClose}
        >
          <Home size={16} className="mr-3 text-gray-500" />
          Home
        </Link>

        <Link
          href="/dashboard"
          className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
          onClick={onClose}
        >
          <LayoutDashboard size={16} className="mr-3 text-gray-500" />
          Dashboard
        </Link>
      </div>

      {/* 로그아웃 */}
      <div>
        <button
          onClick={onLogout}
          className="flex w-full items-center px-4 py-2 text-sm text-gray-600 hover:bg-gray-100"
        >
          <LogOut size={16} className="mr-3 text-gray-500" />
          Sign out
        </button>
      </div>
    </div>
  );
};

export default ProfileDropdown;

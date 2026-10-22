import { MyProfile } from '@/features/member/types';

export interface ProfileDropdownProps {
  user: MyProfile;
  isOpen: boolean;
  onClose: () => void;
  onLogout: () => void;
}

export interface SubscribeModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export interface UserAvatarProps {
  user: MyProfile | null;
  size?: 'sm' | 'md';
}

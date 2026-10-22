import CallbackClient from '@/features/member/components/login/callback/CallbackClient';
import { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Google Login Callback',
  robots: 'noindex, nofollow', // Prevent indexing by search engines
};

export default function GoogleLoginCallbackPage() {
  return <CallbackClient />;
}

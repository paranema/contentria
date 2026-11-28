import CallbackClient from '@/features/member/components/login/callback/CallbackClient';
import { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Google Login Callback',
  // OAuth 콜백 처리 페이지가 검색 엔진에 색인되지 않도록 설정합니다.
  robots: 'noindex, nofollow',
};

export default function GoogleLoginCallbackPage() {
  return <CallbackClient />;
}

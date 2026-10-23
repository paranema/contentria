import React from 'react';
import PolicyClient from '@/app/(home)/policy/_components/PolicyClient';
import { Metadata } from 'next';

export const metadata: Metadata = {
  title: '약관 및 정책',
  description: '서비스 이용 약관 및 개인정보 처리 방침을 확인하세요.',
};

export default function PolicyPage() {
  return (
    // Suspense는 useSearchParams를 사용하는 클라이언트 컴포넌트를 감쌀 때 필수이다.
    // (빌드 타임 에러 방지)
    <React.Suspense fallback={<div className="min-h-screen" />}>
      <PolicyClient />
    </React.Suspense>
  );
}

import React from 'react';
import PolicyClient from '@/app/(home)/policy/_components/PolicyClient';
import { Metadata } from 'next';

export const metadata: Metadata = {
  title: '약관 및 정책',
  description: '서비스 이용 약관 및 개인정보 처리 방침을 확인하세요.',
};

export default function PolicyPage() {
  return (
    // useSearchParams를 사용하는 클라이언트 컴포넌트의 빌드 시점 오류를 방지하기 위해 Suspense로 감쌉니다.
    <React.Suspense fallback={<div className="min-h-screen" />}>
      <PolicyClient />
    </React.Suspense>
  );
}

'use client';

import { useState } from 'react';
import { Menu } from 'lucide-react';
import { Button } from '@/shared/ui/button';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/shared/ui/sheet';
import DashboardNav from './DashboardNav';
import DashboardProfileCard from './DashboardProfileCard';

interface DashboardMobileNavProps {
  blogSlug: string | null;
}

/**
 * 모바일용 대시보드 내비게이션 드로어입니다.
 * 고정 오버레이 버튼 대신 헤더 좌측 영역에 트리거를 배치해 로고 가림을 방지하며, 메뉴 이동 시 드로어를 닫습니다.
 */
export default function DashboardMobileNav({ blogSlug }: DashboardMobileNavProps) {
  const [open, setOpen] = useState(false);

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" className="mr-1 md:hidden" aria-label="메뉴 열기">
          <Menu className="h-5 w-5" />
        </Button>
      </SheetTrigger>

      <SheetContent side="left" className="w-64 p-0">
        {/* 시각적 헤더는 프로필 카드가 대신하며, 제목과 설명은 스크린 리더 접근성을 위해서만 유지합니다. */}
        <SheetHeader className="border-b p-0 text-left">
          <SheetTitle className="sr-only">블로그 관리</SheetTitle>
          <SheetDescription className="sr-only">대시보드 관리 메뉴</SheetDescription>
          <DashboardProfileCard blogSlug={blogSlug} />
        </SheetHeader>

        <DashboardNav blogSlug={blogSlug} onNavigate={() => setOpen(false)} />
      </SheetContent>
    </Sheet>
  );
}

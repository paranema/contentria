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
 * Mobile drawer for the dashboard nav. The trigger renders inside the header's left
 * section (standard `[☰][logo]` pattern) instead of a fixed overlay button, so it can
 * no longer overlap the logo. Navigating closes the sheet.
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
        {/* 시각적 헤더는 프로필 카드가 대신하고, 제목/설명은 스크린 리더용으로만 유지 */}
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

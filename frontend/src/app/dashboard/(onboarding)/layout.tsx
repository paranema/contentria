import React from 'react';

import DashboardHeader from '@/app/dashboard/_components/DashboardHeader';
import Footer from '@/app/_components/Footer';

/**
 * Minimal, distraction-free layout for onboarding (no sidebar/menus): the user has no
 * blog yet, so dashboard navigation would only offer dead ends.
 */
export default function OnboardingLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid h-screen grid-rows-[auto_1fr_auto] bg-gray-50">
      <DashboardHeader blogSlug={null} />

      <main className="mx-auto flex w-full max-w-2xl items-center p-4">
        <div className="w-full">{children}</div>
      </main>

      <Footer />
    </div>
  );
}

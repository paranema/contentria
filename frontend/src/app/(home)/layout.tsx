import Footer from '@/app/_components/Footer';
import HomeHeader from '@/app/(home)/_components/header/HomeHeader';
import { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Home',
  description: 'Welcome to the Home Page',
};

export default function HomeLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid min-h-[100dvh] grid-rows-[auto_1fr_auto] bg-white antialiased">
      <HomeHeader />
      <main className="w-full">{children}</main>
      <Footer />
    </div>
  );
}

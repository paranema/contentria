import Footer from '@/app/_components/Footer';
import SkeletonHeader from '@/app/(home)/_components/SkeletonHeader';
import SkeletonMain from '@/app/(home)/_components/SkeletonMain';

export default function Loading() {
  return (
    <div className="grid min-h-screen grid-rows-[auth_1fr_auto] bg-white antialiased">
      <SkeletonHeader />
      <SkeletonMain />
      <Footer />
    </div>
  );
}

import Image from 'next/image';
import { Card, CardContent } from '@/shared/ui/card';

interface AuthFormCardProps {
  title: string;
  children: React.ReactNode;
}

export default function AuthFormCard({ title, children }: AuthFormCardProps) {
  return (
    <div className="flex w-full flex-col items-center">
      <div className="mb-6 flex justify-center">
        <Image
          src="/contentria-icon.png"
          alt="Contentria Logo"
          width={500}
          height={500}
          className="h-24 w-24 object-contain"
          priority
        />
      </div>

      <h2 className="mb-8 text-center text-3xl font-extrabold text-gray-900">{title}</h2>

      <Card className="w-full border-gray-200 bg-white shadow-md">
        <CardContent className="p-6 sm:p-8">{children}</CardContent>
      </Card>
    </div>
  );
}

import Link from 'next/link';

export default function Footer() {
  return (
    <footer className="mt-auto border-t border-gray-200 bg-white py-8 md:py-6">
      <div className="mx-auto flex w-full max-w-[1440px] flex-col items-center justify-between gap-6 px-4 sm:px-6 md:flex-row lg:px-12">
        <div className="flex flex-col items-center text-center md:items-start md:text-left">
          <p className="mb-2 text-sm text-gray-500 md:mb-4">
            © {new Date().getFullYear()} Contentria. All rights reserved.
          </p>
          <p className="text-sm text-gray-500">
            이메일:{' '}
            <a
              href="mailto:help@contentria.com"
              className="transition-colors hover:text-indigo-600"
            >
              help@contentria.com
            </a>
          </p>
        </div>

        <div className="flex flex-col items-center">
          <div className="flex items-center space-x-4 sm:space-x-6">
            <Link
              href="/policy?tab=privacy"
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-gray-500 transition-colors hover:text-indigo-600"
            >
              개인정보처리방침
            </Link>
            <Link
              href="/policy?tab=terms"
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-gray-500 transition-colors hover:text-indigo-600"
            >
              서비스 이용약관
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}

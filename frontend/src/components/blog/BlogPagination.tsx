import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/shared/ui/pagination';

interface BlogPaginationProps {
  currentPage: number;
  totalPages: number;
  blogSlug: string;
}

export default function BlogPagination({ currentPage, totalPages, blogSlug }: BlogPaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  // URL은 1-based로 생성 (사용자 친화적)
  const createPageUrl = (page: number) => {
    if (page === 0) {
      return `/@${blogSlug}`; // 첫 페이지는 쿼리 스트링 없이
    }
    return `/@${blogSlug}?page=${page + 1}`;
  };

  // 표시할 페이지 범위 계산 (현재 페이지 기준 좌우 2개씩)
  const getVisiblePages = (): (number | 'ellipsis')[] => {
    const delta = 2;
    const range: number[] = [];
    const rangeWithDots: (number | 'ellipsis')[] = [];
    let previousPage: number | undefined;

    for (let i = 0; i < totalPages; i++) {
      if (
        i === 0 || // 첫 페이지
        i === totalPages - 1 || // 마지막 페이지
        (i >= currentPage - delta && i <= currentPage + delta) // 현재 페이지 근처
      ) {
        range.push(i);
      }
    }

    range.forEach((page) => {
      if (previousPage !== undefined) {
        if (page - previousPage === 2) {
          rangeWithDots.push(previousPage + 1);
        } else if (page - previousPage !== 1) {
          rangeWithDots.push('ellipsis');
        }
      }

      rangeWithDots.push(page);
      previousPage = page;
    });

    return rangeWithDots;
  };

  const pages = getVisiblePages();
  const isFirstPage = currentPage === 0;
  const isLastPage = currentPage === totalPages - 1;

  return (
    <Pagination className="mt-8">
      <PaginationContent>
        {/* 이전 버튼 */}
        <PaginationItem>
          <PaginationPrevious
            href={!isFirstPage ? createPageUrl(currentPage - 1) : '#'}
            aria-disabled={isFirstPage}
            className={isFirstPage ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
          />
        </PaginationItem>

        {/* 페이지 번호들 */}
        {pages.map((page, idx) => (
          <PaginationItem key={`page-${idx}`}>
            {page === 'ellipsis' ? (
              <PaginationEllipsis />
            ) : (
              <PaginationLink
                href={createPageUrl(page)}
                isActive={page === currentPage}
                className={page === currentPage ? 'pointer-events-none' : 'cursor-pointer'}
              >
                {page + 1}
              </PaginationLink>
            )}
          </PaginationItem>
        ))}

        {/* 다음 버튼 */}
        <PaginationItem>
          <PaginationNext
            href={!isLastPage ? createPageUrl(currentPage + 1) : '#'}
            aria-disabled={isLastPage}
            className={isLastPage ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  );
}
